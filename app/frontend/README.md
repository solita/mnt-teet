# TEET frontend app

TEET frontend is a Clojurescript Single Page Application.

Most important external libraries:
- [Reagent](https://github.com/reagent-project/reagent) is a React
  wrapper for Clojure
- [Tuck](https://github.com/tatut/tuck) is a micro framework for
  managing state in Reagent apps
- [postgrest-ui](https://github.com/tatut/postgrest-ui) is a library
  of Reagent components for interfacing with databases using
  [PostgREST](http://postgrest.org)
- [Openlayers](https://openlayers.org/) is used for map functionality
- npm, minimun version 7
- npx

# Running locally

Figwheel is used to automatically hot reload code
- Run the local backend
- run npm install (not needed if packages.json unchanged) - npx must be installed too or startup will fail later
- Run `dev.sh` or start a REPL with `dev` profile from your favorite text editor
- Open [http://localhost:4000](http://localhost:4000)
  - [http://localhost:9500](http://localhost:9500 doesn't work because fake authentication from 
    local backend is needed)
  - You should see a login screen with a password field and four test users
  - Copy password from `mnt-teet-private/config.edn` -> `basic-auth-password` and enter it to the 
    password field
  - Click one of the test users to log in
- To see unit test status, open
  [http://localhost:9500/figwheel-extra-main/auto-testing](http://localhost:9500/figwheel-extra-main/auto-testing)

# Unit tests

Unit tests are located under `test/` directory. Unit tests for
e.g. `teet.example.namespace` go to a namespace
`teet.example.namespace-test` in
`test/teet/example/namespace_test.cljs`.

In addition to writing the tests, remember to require the namecpace in
`teet.runner`, in directory `test/teet/runner.cljs`. Without the
inclusion the tests run locally with Figwheel, but are not run as part
of CodeBuild.
