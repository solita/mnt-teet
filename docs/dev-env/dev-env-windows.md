# Installing TEET dev environment on Windows machine.

Configuring TEET dev environment to work on Windows is a bit more struggle than on Mac/Linux, but it can be done.

## Installing and configuring the environment
1. Install Clojure (could also be bundled with for example Leiningen). You can check if it works by executing "clj" in Powershell.
2. Install Node.JS / NPM
3. Install Maven (needed for dev-tools installation, needs to be added to PATH)
4. Install Datomic dev-local (part of Cognitect dev-tools, separate installation, needs to be added to PATH). 
5. Install Docker
6. Create a .datomic/dev-local.edn file in your home directory (C:\Users\xxxxxx\), containing a map with :storage-dir and an absolute path, i.e.: {:storage-dir "C:/Users/xxxxxx/.datomic/"}
7. Install and set up the PostgreSQL (inc. PostGIS extension). Can also be set up in Docker, see "dockerized-pg.notes" file. If you install on your local computer, make sure to update pg_hba.conf to trust local connections.
8. If you installed latest version of Cognitect dev-tools, make sure to update the version in backend\deps.edn file.
9. Make sure you've got your access to AWS CLI and updated the C:\Users\xxxxxx\.aws\credentials file with following:
  [teet-dev]
  region = eu-central-1
  aws_region = eu-central-1
  aws_access_key_id = access-key
  aws_secret_access_key = secret-key
10. Clone/pull from Github both mnt-teet and mnt-teet-private so that they are in the same folder
11. Create a symbolic link between backend and common as it does not work automatically in Windows (NB! Might need you to delete the "common" file from backend folder first)  (mklink -D "C:\..\app\backend\common\" "C:\..\app\common\")
12. Using cmd, navigate to .\app\frontend\ and execute "npm install". This will install all the front-end dependencies and will take time. 
13. Using Powershell, try to execute backend by navigating to that folder and executing "clj -A:dev". It should start up the REPL but might not until the next two sections are done.

## Data import recommendations
1. Make sure you have the PostgREST (and PostgreSQL, if used) docker apps running. For PostgREST app you may run the docker_postgrest_dev_windows.cmd file in .\app\api\ folder. It should set up the image with latest version of PostgREST.
2. You may try to use the existing shell scripts in Windows, but it is recommended to execute the tailored db-setup-windows.bat file that can be found in .\db\ folder. Make sure to first navigate to that folder within cmd.exe.
3. If the database objects and flyway migration have been successful, navigate in Powershell to .\app\datasource-import\ and run "clojure -A:import example-config.edn". The process should successfully import different land registry, etc. data.
4. If there are any 401 errors (error message includes "JWTIssuedAtFuture"), you may need to edit the file .\app\common\src\clj\teet.auth\jwt_token.clj and remove the option ":iat (numeric-date (Date.))" from create-token function.
5. If all is successful, you can create the test users by running in REPL (make-mock-users!) and afterwards grant admin access by executing (give-admin-permission [:user/person-id "EE12345678910"]).

## What needs to be done in IDEA / front-end
1. If you are using IntelliJ IDEA (recommended alongside with Cursive plugin), you need to do few things in order to run the front-end and back-end.
2. First create a new Clojure Deps project from mnt-teet folder.
3. If you have the folder open, right-click on app\backend folder on the navigation windows and select "New > Module". It should open you a new Clojure Deps module window, where you just need to make sure the name, directories and SDK is correct.
4. Once module(s) have been created, open the "Run/Debug configuration" window (top right, from dropdown select "edit configurations") and add new configuration Clojure REPL > Local.
    1. First do it from the frontend directory, selecting options: REPL type: clojure.main; Run with IntelliJ project classpath; Parameters: dev/user.clj
    2. For the backed the options are: REPL type: nREPL; Run with Deps; Aliases: dev; Environment: AWS_REGION=eu-central-1 (might not be necessary, try without).
5. Then first try to run the frontend REPL and if it is successful, then backend and if that works, run (restart).

## Additional tips and tricks from #clojure if you choose to use Windows or WSL
Using Windows for Dev
  1. https://github.com/clojure/tools.deps.alpha/wiki/clj-on-Windows
  2. Some clojure tools don't support Windows that well (e.g. lein-tools-deps).
  3. Long path names or long classpaths (https://clojure.atlassian.net/browse/TDEPS-120) will make some things fail (e.g. shadow-cljs).
Using WSL2 for Dev
  1. You need to have your code in WSL2 file system.
  2. You need to run your IDE also in WSL2 (e.g. IntelliJ Idea). If you run IDE on Windows, the file access from Windows to WSL2 is reaaaaally (https://docs.microsoft.com/en-us/windows/wsl/compare-versions#comparing-features) slow and makes work impossible. Also file watches don't work, etc.
  3. You will need some X Server (https://sourceforge.net/projects/vcxsrv/) to show the IDE graphically. Coming built-in to WSL2 in future. (https://devblogs.microsoft.com/commandline/whats-new-in-the-windows-subsystem-for-linux-september-2020/#gui-apps)
  4. Docker: host.docker.internal points to the Windows host (if you have installed Docker for Windows using WSL2). May need some workarounds.
Misc (Docker)
  1. Docker unable to build one image: workaround (https://github.com/microsoft/WSL/issues/4694#issuecomment-556095344)
  2. Reduce the WSL2 max memory (https://docs.microsoft.com/en-us/windows/wsl/wsl-config#wsl-2-settings) so that Docker does not eat up all the memory.