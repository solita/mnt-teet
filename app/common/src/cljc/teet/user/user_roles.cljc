(ns teet.user.user-roles)


(defn has-role?
  "Returns true if user has the given role or one of the given roles.

  If the input is a single role keyword, checks that the user has that role.
  If the input is a collection of role keywords, checks that user has at least
  one of the roles."
  ([{roles :user/roles :as user} role-or-roles]
   (cond
     (nil? role-or-roles)
     true

     (keyword? role-or-roles)
     (contains? roles role-or-roles)

     :else
     (some #(has-role? user %) role-or-roles))))

(defn require-role [user role-or-roles]
  (when-not (has-role? user role-or-roles)
    (throw (ex-info "User doesn't have the required role."
                    {:user/roles (:user/roles user)
                     :required-role role-or-roles}))))
