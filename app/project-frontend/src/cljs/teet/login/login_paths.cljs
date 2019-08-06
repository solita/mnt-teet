(ns teet.login.login-paths
  "Define app state paths for login items")


(def token
  "Token to the current JWT token for the logged in session."
  [:login :tokens "id_token"])
