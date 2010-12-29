(use 'ring.adapter.jetty)
;(require 'datatool.api)
(use 'datatool.api) ;replacing requre with use because I don't want to refer to namespace explicitly (does refer as well)

;(let [port (Integer/parseInt (get (System/getenv) "PORT" "80"))] ...)
  (run-jetty main-routes {:port 81})
