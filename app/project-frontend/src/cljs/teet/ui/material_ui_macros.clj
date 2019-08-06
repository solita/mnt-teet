(ns teet.ui.material-ui-macros)

(defmacro define-mui-component [sym]
  `(let [c# (delay
              (let [mui-class# (goog.object/get @teet.ui.material-ui/MaterialUI ~(name sym))]
                (if-not mui-class#
                  (.error js/console "No MaterialUI class found: " ~(name sym))
                  (reagent.core/adapt-react-class mui-class#))))]
     (defn ~sym [& args#]
       (into [@c#] args#))))

(defmacro define-mui-components [& syms]
  `(do
     ~@(for [sym syms]
         `(define-mui-component ~sym))))
