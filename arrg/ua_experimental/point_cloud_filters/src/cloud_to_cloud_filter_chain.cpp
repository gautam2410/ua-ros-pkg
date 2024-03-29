/*
 * Copyright (c) 2008, Willow Garage, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Willow Garage, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */


#include "ros/ros.h"
#include "sensor_msgs/PointCloud.h"
#include "message_filters/subscriber.h"
#include "tf/message_filter.h"
#include "tf/transform_listener.h"
#include "filters/filter_chain.h"

class CloudToCloudFilterChain
{
protected:
  // Our NodeHandle
  ros::NodeHandle nh_;
  ros::NodeHandle private_nh_;

  // Components for tf::MessageFilter
  tf::TransformListener tf_;
  message_filters::Subscriber<sensor_msgs::PointCloud> cloud_sub_;
  message_filters::Subscriber<sensor_msgs::PointCloud> no_sub_;
  tf::MessageFilter<sensor_msgs::PointCloud> tf_filter_;

  // Filter Chain
  filters::FilterChain<sensor_msgs::PointCloud> filter_chain_;

  // Components for publishing
  sensor_msgs::PointCloud msg_;
  ros::Publisher output_pub_;

  // Deprecation helpers
  ros::Timer deprecation_timer_;
  bool  using_filter_chain_deprecated_;

public:
  // Constructor
  CloudToCloudFilterChain() :
    private_nh_("~"),
    cloud_sub_(nh_, "cloud_in", 50),
    tf_filter_(cloud_sub_, tf_, "", 50),
    filter_chain_("sensor_msgs::PointCloud")
  {
    // Configure filter chain
    
    using_filter_chain_deprecated_ = private_nh_.hasParam("filter_chain");

    if (using_filter_chain_deprecated_)
      filter_chain_.configure("filter_chain", private_nh_);
    else
      filter_chain_.configure("cloud_filter_chain", private_nh_);
    
    std::string tf_message_filter_target_frame;

    if (private_nh_.hasParam("tf_message_filter_target_frame"))
    {
      private_nh_.getParam("tf_message_filter_target_frame", tf_message_filter_target_frame);

      tf_filter_.setTargetFrame(tf_message_filter_target_frame);
      tf_filter_.setTolerance(ros::Duration(0.03));

      // Setup tf::MessageFilter generates callback
      tf_filter_.registerCallback(boost::bind(&CloudToCloudFilterChain::callback, this, _1));
    }
    else 
    {
      tf_filter_.connectInput(no_sub_);
      // Pass through if no tf_message_filter_target_frame
      cloud_sub_.registerCallback(boost::bind(&CloudToCloudFilterChain::callback, this, _1));
    }
    
    // Advertise output
    output_pub_ = nh_.advertise<sensor_msgs::PointCloud>("cloud_out", 1000);

    // Set up deprecation printout
    deprecation_timer_ = nh_.createTimer(ros::Duration(5.0), boost::bind(&CloudToCloudFilterChain::deprecation_warn, this, _1));
  }
  
  // Deprecation warning callback
  void deprecation_warn(const ros::TimerEvent& e)
  {
    if (using_filter_chain_deprecated_)
      ROS_WARN("Use of '~filter_chain' parameter in cloud_to_cloud_filter_chain has been deprecated. Please replace with '~cloud_filter_chain'.");
  }

  // Callback
  void callback(const sensor_msgs::PointCloud::ConstPtr& msg_in)
  {
    // Run the filter chain
    filter_chain_.update (*msg_in, msg_);
    
    // Publish the output
    output_pub_.publish(msg_);
  }
};

int main(int argc, char **argv)
{
  ros::init(argc, argv, "cloud_to_cloud_filter_chain");
  
  CloudToCloudFilterChain t;
  ros::spin();
  
  return 0;
}
