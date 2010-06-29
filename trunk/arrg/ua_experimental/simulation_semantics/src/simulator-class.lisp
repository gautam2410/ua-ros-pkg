(in-package :simulation_semantics)

;;=========================================================
;; Simulator Class Definition

(define-unit-class simulator ()
  ((objects :initform '())
   (goal-map :initform (make-hash-table :test 'eq))
   (policy-map :initform (make-hash-table :test 'eq))
   (termination-time :initform 15)
   ;; "private" members
   (current-time :initform 0)
   ;; links
   (simulations :link (simulation simulator :singular t))
   (suppressed-objects :initform (make-hash-table :test 'eq)))

  (:initial-space-instances (simulator-library))
)

(defmethod duplicate ((this simulator) duplicate-name)
  (make-instance 'simulator
                 :instance-name duplicate-name
                 :objects (copy-list (objects-of this))
                 :goal-map (copy-hash-table (goal-map-of this))
                 :policy-map (copy-hash-table (policy-map-of this))
                 :termination-time (termination-time-of this)
                 :current-time 0))

;; Found on the web
(defun copy-hash-table (table &key key test size
                                   rehash-size rehash-threshold)
  "Returns a copy of hash table TABLE, with the same keys and values
as the TABLE. The copy has the same properties as the original, unless
overridden by the keyword arguments.

Before each of the original values is set into the new hash-table, KEY
is invoked on the value. As KEY defaults to CL:IDENTITY, a shallow
copy is returned by default."
  (setf key (or key 'identity))
  (setf test (or test (hash-table-test table)))
  (setf size (or size (hash-table-size table)))
  (setf rehash-size (or rehash-size (hash-table-rehash-size table)))
  (setf rehash-threshold (or rehash-threshold (hash-table-rehash-threshold table)))
  (let ((copy (make-hash-table :test test :size size
                               :rehash-size rehash-size
                               :rehash-threshold rehash-threshold)))
    (maphash (lambda (k v)
               (setf (gethash k copy) (funcall key v)))
             table)
    copy))



;;=========================================================
;; Simulator Class Methods

;; This is a bit harsher on the simulator (kills everything when it is done)
(defmethod run-simulator ((sim simulator))
  ;; Create the simulator and prepare for execution
  (add-instance-to-space-instance sim (find-space-instance-by-path '(running-simulators)))
  (construct sim)
  (create-simulation sim)
  (start sim)
  (loop for obj in (objects-of sim)
     do (activate obj))

  ;; Main loop of the simulator
  (loop with step = 0.1
     until (should-terminate? sim)
     for last-state = (first (find-instances 'world-state (current-simulation sim) :all))
     do (format t "~%~%Time Step ~,3f:~%" (current-time-of sim)) 
       (loop for obj being the hash-keys of (policy-map-of sim) using (hash-value policy)
          if policy do 
            (funcall policy obj (current-time-of sim) *current-state*)
          else do 
            (format t "~a is doing nothing.~%" obj))
       (if last-state 
           (loop for p in (predicates-of last-state) do (print p)))
       (if last-state
           (loop for p in (predicates-of last-state)
              when (and (eq (first p) 'dist-to-goal) (< (first (last p)) 0.5))
              do (setf (success-of (current-simulation sim)) t)))
       (incf (current-time-of sim) step)
       (sleep step))

  ;; Tear down the simulator
  (loop for obj in (objects-of sim)
     do (deactivate obj))
  (pause sim)
  (destroy sim)
  (setf (current-time-of sim) 0)
  (remove-instance-from-space-instance sim (find-space-instance-by-path '(running-simulators)))
  
  ;; Return the "success" of the simulator (kind of a hack for now)
  (success-of (current-simulation sim))
)

(defmethod should-terminate? ((sim simulator))
  (or (>= (current-time-of sim) (termination-time-of sim))
      (success-of (current-simulation sim))))

(defmethod goal-satisfied ((sim simulator))
  (loop for obj being the hash-keys of (goal-map-of sim) using (hash-value goal)
     if goal do
       (funcall goal obj)))

(defmethod suppress ((sim simulator) obj)
  "Cause the simulator to run without a particular object being created."
  (setf (gethash obj (suppressed-objects-of sim)) t))

(defmethod revive ((sim simulator) obj)
  "Restore a particular object to the simulator."
  (remhash obj (suppressed-objects-of sim)))
  
(defmethod construct ((sim simulator))
  "Adds the objects to the simulator, unless they are suppressed."
  (loop for obj in (objects-of sim)
     when (null (gethash obj (suppressed-objects-of sim)))
     do (add-to-world obj)
       (sleep 0.5)))

(defmethod destroy ((sim simulator))
  (loop for obj in (objects-of sim)
     when (null (gethash obj (suppressed-objects-of sim)))
     do (remove-from-world obj)
       (sleep 0.5)))

(defmethod start ((sim simulator))
  (call-service "gazebo/unpause_physics" 'std_srvs-srv:Empty))

;; Note: Gazebo does not publish a time when paused
(defmethod pause ((sim simulator))
  (call-service "gazebo/pause_physics" 'std_srvs-srv:Empty))
