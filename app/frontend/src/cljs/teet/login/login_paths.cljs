(ns teet.login.login-paths
  "Define app state paths for login items")


(def api-token
  "Token to the current JWT token for the logged in session."
  [:user :api-token])
