(ns datatool.util
    (:require [datatool.db :as db])
    (:require [clojure.contrib.str-utils2 :as s])
    (:require [clj-time.core :as t])
    (:require [clj-time.coerce :as t-c])
    (:require [clj-time.format  :as t-f])
)

(def validChars (map char (concat (range 48 58) (range 65 91) (range 97 123))))
(defn random-char [] (nth validChars (rand (count validChars))))
(defn random-str [length] (apply str (take length (repeatedly random-char))))
(defn random-int [start end] (nth (range (start (+ 1 end))) (+ start (rand (- end start)))))
(defn random-vector-of-strings [vector-length-upto string-length-from string-length-to] ;;e.g. random-vector-of-strings 5 3 8, e.g. ["9HO0" "ge9D7H" "Swiovua"]
    (into [] (for [i (range (rand vector-length-upto))] (random-str (rand-nth (range string-length-from string-length-to))) )))

(defn generateSampleDatatypes [number]
  (for [i (range number)] ;;figure out a dotimes
    (let [name (random-str 10)
          tags (random-vector-of-strings 5 3 8) ;;["9HO0" "ge9D7H" "Swiovua"]
          fields  (map (fn [x] {:name x :type "String"} ) (random-vector-of-strings 5 2 6) )
          comments (random-str 15)
          mongo-datatype {:datatype_name name, :tags tags, :fields fields, :comments comments}]
          (db/add-item! :datatypes mongo-datatype);;TODO: handle possible errors?
          mongo-datatype
)))

(defn date-to-string [#^java.lang.Long timestamp]
  (if (not (nil? timestamp))
    (let [f1 (t-f/formatters :year-month-day)
         f2 (t-f/formatters :hour-minute)]
          (str (t-f/unparse f1 (t-c/from-long timestamp)) " " (t-f/unparse f2 (t-c/from-long timestamp))))
  ""))

;turns ("str1" "str2") into "str1 str2"
(defn compact-str-list [s]
  (s/trim (apply str (map #(str % " ") s))))

