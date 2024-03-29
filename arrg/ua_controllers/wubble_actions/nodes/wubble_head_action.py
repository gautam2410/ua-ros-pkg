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

# Author: Anh Tran

PKG = 'wubble_actions'
NAME = 'wubble_head_action'

import roslib; roslib.load_manifest(PKG)
import rospy
import tf

from actionlib import SimpleActionServer

from wubble_actions.msg import *
from std_msgs.msg import Float64
from geometry_msgs.msg import PointStamped
from pr2_controllers_msgs.msg import JointControllerState

import math


class WubbleHeadActionServer():

    def __init__(self):

        # Initialize constants
        self.JOINTS_COUNT = 2                           # Number of joints to manage
        self.ERROR_THRESHOLD = 0.02                     # Report success if error reaches below threshold (0.015 also works)
        self.TIMEOUT_THRESHOLD = rospy.Duration(15.0)   # Report failure if action does not succeed within timeout threshold

        # Initialize new node
        rospy.init_node(NAME, anonymous=True)

        # Initialize publisher & subscriber for pan
        self.head_pan_frame = 'head_pan_link'
        self.head_pan = JointControllerState(set_point=0.0, process_value=0.0, error=1.0)
        self.head_pan_pub = rospy.Publisher('head_pan_controller/command', Float64)
        rospy.Subscriber('head_pan_controller/state', JointControllerState, self.read_current_pan)
        rospy.wait_for_message('head_pan_controller/state', JointControllerState)

        # Initialize publisher & subscriber for tilt
        self.head_tilt_frame = 'head_tilt_link'
        self.head_tilt = JointControllerState(set_point=0.0, process_value=0.0, error=1.0)
        self.head_tilt_pub = rospy.Publisher('head_tilt_controller/command', Float64)
        rospy.Subscriber('head_tilt_controller/state', JointControllerState, self.read_current_tilt)
        rospy.wait_for_message('head_tilt_controller/state', JointControllerState)

        # Initialize tf listener
        self.tf = tf.TransformListener()

        # Initialize point action server
        self.result = WubbleHeadResult()
        self.feedback = WubbleHeadFeedback()
        self.feedback.head_position = [self.head_pan.process_value, self.head_tilt.process_value]
        self.server = SimpleActionServer(NAME, WubbleHeadAction, self.execute_callback)

        # Reset head position
        rospy.sleep(1)
        self.reset_head_position()
        rospy.loginfo("%s: Ready to accept goals", NAME)


    def read_current_pan(self, pan_data):
        self.head_pan = pan_data
        self.has_latest_pan = True


    def read_current_tilt(self, tilt_data):
        self.head_tilt = tilt_data
        self.has_latest_tilt = True
    

    def reset_head_position(self):
        self.head_pan_pub.publish(0.0)
        self.head_tilt_pub.publish(0.0)
        rospy.sleep(5)


    def wait_for_latest_controller_states(self, timeout):
        self.has_latest_pan = False
        self.has_latest_tilt = False
        r = rospy.Rate(100)
        start = rospy.Time.now()
        while (self.has_latest_pan == False or self.has_latest_tilt == False) and (rospy.Time.now() - start < timeout):
            r.sleep()


    def transform_target_point(self, point):
        pan_target_frame = self.head_pan_frame
        tilt_target_frame = self.head_tilt_frame
        
        # Wait for tf info (time-out in 5 seconds)
        self.tf.waitForTransform(pan_target_frame, point.header.frame_id, rospy.Time(), rospy.Duration(5.0))
        self.tf.waitForTransform(tilt_target_frame, point.header.frame_id, rospy.Time(), rospy.Duration(5.0))

        # Transform target point to pan reference frame & retrieve the pan angle
        pan_target = self.tf.transformPoint(pan_target_frame, point)
        pan_angle = math.atan2(pan_target.point.y, pan_target.point.x)
        #rospy.loginfo("%s: Pan transformed to <%s, %s, %s> => angle %s", \
        #        NAME, pan_target.point.x, pan_target.point.y, pan_target.point.z, pan_angle)

        # Transform target point to tilt reference frame & retrieve the tilt angle
        tilt_target = self.tf.transformPoint(tilt_target_frame, point)
        tilt_angle = math.atan2(tilt_target.point.z,
                math.sqrt(math.pow(tilt_target.point.x, 2) + math.pow(tilt_target.point.y, 2)))
        #rospy.loginfo("%s: Tilt transformed to <%s, %s, %s> => angle %s", \
        #        NAME, tilt_target.point.x, tilt_target.point.y, tilt_target.point.z, tilt_angle)

        return [pan_angle, tilt_angle]


    def execute_callback(self, goal):
        r = rospy.Rate(100)
        self.result.success = True
        self.result.head_position = [self.head_pan.process_value, self.head_tilt.process_value]
        rospy.loginfo("%s: Executing move head", NAME)
        
        # Initialize target joints
        target_joints = list()
        for i in range(self.JOINTS_COUNT):
            target_joints.append(0.0)
        
        # Retrieve target joints from goal
        if (len(goal.target_joints) > 0):
            for i in range(min(len(goal.target_joints), len(target_joints))):
                target_joints[i] = goal.target_joints[i] 
        else:
            try:
                # Try to convert target point to pan & tilt angles
                target_joints = self.transform_target_point(goal.target_point)
            except (tf.Exception, tf.ConnectivityException, tf.LookupException):
                rospy.loginfo("%s: Aborted: Transform Failure", NAME)
                self.result.success = False
                self.server.set_aborted()
                return

	    # Publish goal command to controllers
        self.head_pan_pub.publish(target_joints[0])
        self.head_tilt_pub.publish(target_joints[1])

        # Initialize loop variables
        start_time = rospy.Time.now()

        while (math.fabs(target_joints[0] - self.head_pan.process_value) > self.ERROR_THRESHOLD or \
                math.fabs(target_joints[1] - self.head_tilt.process_value) > self.ERROR_THRESHOLD):
		
	        # Cancel exe if another goal was received (i.e. preempt requested)
            if self.server.is_preempt_requested():
                rospy.loginfo("%s: Aborted: Action Preempted", NAME)
                self.result.success = False
                self.server.set_preempted()
                break

            # Publish current head position as feedback
            self.feedback.head_position = [self.head_pan.process_value, self.head_tilt.process_value]
            self.server.publish_feedback(self.feedback)
            
            # Abort if timeout
            current_time = rospy.Time.now()
            if (current_time - start_time > self.TIMEOUT_THRESHOLD):
                rospy.loginfo("%s: Aborted: Action Timeout", NAME)
                self.result.success = False
                self.server.set_aborted()
                break

            r.sleep()
        
        if (self.result.success):
            rospy.loginfo("%s: Goal Completed", NAME)
            self.wait_for_latest_controller_states(rospy.Duration(2.0))
            self.result.head_position = [self.head_pan.process_value, self.head_tilt.process_value]
            self.server.set_succeeded(self.result)



if __name__ == '__main__':
    try:
        w = WubbleHeadActionServer()
        rospy.spin()
    except rospy.ROSInterruptException:
        pass

