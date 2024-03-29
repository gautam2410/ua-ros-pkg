(in-package :simulation_semantics)

(defparameter *we-care* nil)
(defparameter *simulation* nil)
(defparameter *got-ws* nil)

(defun start-session ()
  (start-ros-node "learner")
  (init-objects)
  (imagine 'robot)
  (setf *simulation* (make-space-instance (list (gensym)) :class 'simulation ))
  (subscribe "gazebo_world_state" "simulator_experiments/WorldState" #'one-world-handler)
  (format t "Ready!~%"))

(defun stop ()
  (unwind-protect (clear)
    (setf *we-care* nil)
    (setf *simulation* nil)
    (setf *got-ws* nil)
    (shutdown-ros-node)
    )
  )

(defun imagine (what &optional where)
  (let* ((old (find-instance-by-name what))
         (it (if old
                 old
                 (make-instance 'physical-object 
                                :instance-name what
                                :gazebo-name (string what)
                                :xyz (if where where '(2 0 0.2))))))
    (if it 
        (add-to-world it)
        (format t "Sorry, I don't know what that is.~%"))))

(defun forget (what)
  (let* ((it (find-instance-by-name what)))
    (if it 
        (remove-from-world it)
        (format t "Sorry, I don't know what that is.~%"))))

(defun clear ()
  (call-service "delete_all_models" 'std_srvs-srv:Empty))

(defun describe-world ()
  (setf *we-care* t)
  (while (not *got-ws*)
    (sleep 0.1))
  (let* ((ws (first (find-instances 'world-state *simulation* :all))))
    (print-predicates ws))
  (setf *got-ws* nil))

(defun one-world-handler (msg)
  (if *we-care*
      (let* ((ws (translate-world-state msg *simulation*)))
        (annotate-with-predicates ws)
        (setf *we-care* nil)
        (setf *got-ws* t))))

;;=========================================================================
;; New Stuff

(defun get-models () 
  (let ((names (gazebo-srv:model_names-val (call-service "/gazebo/get_world_properties" 
                                                         'gazebo-srv:GetWorldProperties))))
    (setf names (remove "clock" names :test 'string-equal))
    (setf names (remove "point_white" names :test 'string-equal))))