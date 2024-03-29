#!/usr/bin/env python
import roslib; roslib.load_manifest('plotter')
import rospy

#from multiprocessing import Process
import matplotlib
matplotlib.use("cairo")
import matplotlib.pyplot as plt

import time

from plotter.msg import *
from plotter.srv import *

location = roslib.packages.get_pkg_subdir("plotter", "plots") + "/"
print location

def plot(plot_data):
    plt.plot(plot_data.x_data, plot_data.y_data, label=plot_data.name)

    plt.xlabel(plot_data.x_label)
    plt.ylabel(plot_data.y_label)
    plt.title(plot_data.name)
    #plt.legend(loc=0)
    
    plt.draw()
    
def handle_plot(req):
    t = time.time()
    plt.clf()
    ax = plt.subplot(111)
    plt.subplots_adjust(top=0.6)
    for plot_data in req.plots:
        plot(plot_data)
    labels = [line.get_label() for line in ax.lines]
    plt.figlegend(ax.lines, labels, 'upper right')
    plt.savefig(location + str(t) + '.png')
    
    return PlotResponse()

def plot_server():
    rospy.init_node('plotter')
    s = rospy.Service('plot', Plot, handle_plot)
    plt.ion()
    print "Ready to plot things!", plt.isinteractive()
    
    rospy.spin()

if __name__ == "__main__":
    try:
        plot_server()
    except rospy.ROSInterruptException: pass