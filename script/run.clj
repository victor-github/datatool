(use 'ring.adapter.jetty)
(require 'datatool.core)

(let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (run-jetty #'datatool.core/app {:port port}))
