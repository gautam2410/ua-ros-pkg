#! /usr/bin/env python

# Copyright (c) 2010, Arizona Robotics Research Group, University of Arizona
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
#     * Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above copyright
#       notice, this list of conditions and the following disclaimer in the
#       documentation and/or other materials provided with the distribution.
#     * Neither the name of the Willow Garage, Inc. nor the names of its
#       contributors may be used to endorse or promote products derived from
#       this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 'AS IS'
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
#
# Authors: Antons Rebguns
#          Tom Walsh
#

import roslib; roslib.load_manifest('wubble_blocks')
import rospy

from actionlib import SimpleActionClient
from actionlib_msgs.msg import GoalStatus
from geometry_msgs.msg import Twist
from geometry_msgs.msg import PoseArray
from sensor_msgs.msg import LaserScan

from wubble_actions.msg import ErraticBaseGoal
from wubble_actions.msg import ErraticBaseAction
from wubble_actions.msg import SmartArmGoal
from wubble_actions.msg import SmartArmGripperGoal
from wubble_actions.msg import SmartArmAction
from wubble_actions.msg import SmartArmGripperAction

from ax12_controller_core.srv import SetSpeed

class ObjectSwat:
    def __init__(self):
        self.all_objects = []
        self.min_dist = 10
        self.front_dist = 10
        self.avg_front_dist = 10

        rospy.init_node('object_swatter', anonymous=True)
        rospy.Subscriber('overhead_objects', PoseArray, self.update_object_positions)
        rospy.Subscriber('tilt_laser/scan', LaserScan, self.filter_scan)

        self.cmd_vel_pub = rospy.Publisher('cmd_vel', Twist)

        self.sh_pan_speed_srv = rospy.ServiceProxy('shoulder_pan_controller/set_speed', SetSpeed)
        self.base_client = SimpleActionClient('erratic_base_action', ErraticBaseAction)
        self.arm_client = SimpleActionClient('smart_arm_action', SmartArmAction)
        self.gripper_client = SimpleActionClient('smart_arm_gripper_action', SmartArmGripperAction)

        rospy.wait_for_service('shoulder_pan_controller/set_speed')
        self.base_client.wait_for_server()
        self.arm_client.wait_for_server()
        self.gripper_client.wait_for_server()

        rospy.loginfo('Connected to all action servers')

    def update_object_positions(self, msg):
        self.all_objects = msg.poses

    def filter_scan(self, scan):
        self.min_dist = min(scan.ranges)
        num_scans = len(scan.ranges)
        center_scan_index = (num_scans + 1) / 2
        self.front_dist = scan.ranges[center_scan_index]

        min_idx = center_scan_index - 10
        max_idx = center_scan_index + 10
        sum_dist = 0
        for dist in scan.ranges[min_idx:max_idx]:
            sum_dist += dist
        self.avg_front_dist = sum_dist / (max_idx - min_idx)

    def tuck_arm_for_navigation(self):
        goal = SmartArmGripperGoal()
        goal.target_joints = [0.15, -0.15]

        self.gripper_client.send_goal(goal)
        self.gripper_client.wait_for_result()
        result = self.gripper_client.get_result()
        if result.success: print "Gripper in position"
        else: print "Gripper positioning failed"

        goal = SmartArmGoal()
        goal.target_joints = [0.0, 1.77, 0.0, 0.0]
        self.arm_client.send_goal(goal)
        self.arm_client.wait_for_result()
        result = self.arm_client.get_result()
        if result.success: print "Arm tucked"; return True
        else: print "Failed to tuck arm"; return False

    def go_to_object(self, idx):
        goal = ErraticBaseGoal()
        goal.target_pose.header.stamp = rospy.Time.now()
        goal.target_pose.header.frame_id = '/map'
        goal.target_pose.pose = self.all_objects[idx]
        goal.vicinity_range = 0.3

        self.base_client.send_goal(goal)
        self.base_client.wait_for_result()

        if self.base_client.get_state() == GoalStatus.SUCCEEDED:
            result = self.base_client.get_result()
            rospy.loginfo('Robot is in front of the object'); return True
        elif self.base_client.get_state() == GoalStatus.PREEMPTED:
            print "Action pre-empted"; return False
        else:
            print "Action failed"; return False

    def position_self(self):
        max_speed = 0.01
        cmd_vel = Twist()

        rospy.loginfo("position self %f %f %f", self.front_dist, self.avg_front_dist, self.min_dist)

        # Check distance from the laser
        if self.avg_front_dist > 0.29:
            # move in closer
            rospy.loginfo("Object is too far away, closing in")
            range = abs(self.avg_front_dist - 0.29)
            cmd_vel.linear.x = max_speed

            r = rospy.Rate(1000)
            et = rospy.Time.now() + rospy.Duration.from_sec(range / max_speed)
            while rospy.Time.now() < et:
                self.cmd_vel_pub.publish(cmd_vel)
                r.sleep()
            cmd_vel.linear.x = 0.0
            self.cmd_vel_pub.publish(cmd_vel)

        elif self.avg_front_dist < 0.23:
            # backup away
            rospy.loginfo("Object is too far away, backing away")
            range = abs(self.avg_front_dist - 0.29)
            cmd_vel.linear.x = -max_speed
            self.cmd_vel_pub.publish(cmd_vel)
            rospy.sleep(range / max_speed)
            cmd_vel.linear.x = 0.0
            self.cmd_vel_pub.publish(cmd_vel)

        else:
            rospy.loginfo("position self %f %f %f", self.front_dist, self.avg_front_dist, self.min_dist)


    def swat_object(self):
        goal = SmartArmGripperGoal()
        goal.target_joints = [0.15, -0.15]

        self.gripper_client.send_goal(goal)
        self.gripper_client.wait_for_result()
        result = self.gripper_client.get_result()
        if result.success: print "Gripper in position"
        else: print "Gripper positioning failed"

        goal = SmartArmGoal()
        goal.target_joints = [0.0, 1.77, 0.0, 0.0]
        self.arm_client.send_goal(goal)
        self.arm_client.wait_for_result()
        result = self.arm_client.get_result()
        if result.success: print "Arm UP"
        else: print "Failed to raise arm"

        goal.target_joints = [0.93, -0.63, 0.0, 0.0]
        self.arm_client.send_goal(goal)
        self.arm_client.wait_for_result()
        result = self.arm_client.get_result()
        if result.success: print "Arm ready to swat"
        else: print "Failed to position the arm for swatting"

        t = rospy.Time.now()
        print "Swatting action at " + str(t.secs) + "." + str(t.nsecs)

        self.sh_pan_speed_srv(3.0)

        goal.target_joints = [-0.93, -0.63, 0.0, 0.0]
        self.arm_client.send_goal(goal)
        self.arm_client.wait_for_result()
        result = self.arm_client.get_result()
        if result.success: print "Arm just swatted"
        else: print "Arm failed to swat"

        self.sh_pan_speed_srv(1.17)
        rospy.sleep(5)

if __name__ == '__main__':
    try:
        swat = ObjectSwat()

        r = rospy.Rate(0.2)

        while not rospy.is_shutdown():
            rospy.loginfo('There are %d objects in the world' % len(swat.all_objects))

            for idx in range(len(swat.all_objects)):
                if swat.tuck_arm_for_navigation():
                    if swat.go_to_object(idx):
                        swat.position_self()
                        swat.swat_object()

            r.sleep()
    except rospy.ROSInterruptException:
        pass