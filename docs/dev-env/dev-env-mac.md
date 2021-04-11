# Installing TEET dev environment on Mac machine - without docker.

## Installing and configuring the environment
1. Install Clojure (could also be bundled with for example Leiningen). You can check if it works by executing "clj" in a terminal
2. Install Node.JS / NPM
3. Install Maven (needed for dev-tools installation, needs to be added to PATH)
4. Install AWS CLI (https://aws.amazon.com/cli/)
5. Install Datomic dev-local (part of Cognitect dev-tools, separate installation, needs to be added to PATH).
        Just install Cognitect dev-tools (which includes REBL and Datomic dev-local) by requesting an email from cognitech. The email received from cognitech has a link "maven configuration". This step has to be performed (create a settings.html file in user /.m2 folder)
6. Create a .datomic/dev-local.edn file in your home directory 
   (C:\Users\xxxxxx\), containing a map with :storage-dir and an absolute 
   path, i.e.: {:storage-dir "C:/Users/xxxxxx/.datomic/"}
7. Install postgres.app for MAC (includes PostGIS extension)
8. If you have installed latest version of Cognitect dev-tools, make sure to update the version in backend\deps.edn file.
9. Request access (ask in the team) to AWS (https://intra.solita.fi/pages/viewpage.action?pageId=62261630) and udate the your user's ~/.aws/credentials file with following:
  [teet-dev]
  region = eu-central-1
  aws_region = eu-central-1
  aws_access_key_id = access-key
  aws_secret_access_key = secret-key
  
  run $ aws configure if the credentials file does not exist. 
10. Clone from Github both mnt-teet and mnt-teet-private so that they are in the same folder
11. Create a symbolic link between backend and common as it does not work automatically in Windows (NB! Might need you to delete the "common" file from backend folder first)  (mklink -D "C:\..\app\backend\common\" "C:\..\app\common\") --- maybe not necessary for mac
12. On a terminal, navigate to .\app\frontend\ and execute "npm install".
13. On a terminal, try to execute backend by navigating to that folder and executing "clj -A:dev". It should start up the REPL but might not until the next two sections are done.
00
## Data import
1. Connect and start PostgREST by running ./app/api/dev2.sh
2. Run ./db/devdb_create_template.sh
3. Run ./db/devdb_clean.sh. If you get an error (about "migrating schema "public" on schema 17") comment out the DROP FUNCTION line with -- in db/src/main/resources/db/migration/V17__remove_unused_rpc.sql file
5. navigate to app/datasource-import and run "clojure -A:import example-config.edn" (it takes a while to complete).The process should successfully import different land registry, etc. data.
6. If all is successful, you can create the test users by running in REPL (make-mock-users!) and afterwards grant admin access by executing (give-admin-permission [:user/person-id "EE12345678910"]).

## What needs to be done in IDEA / front-end
 IntelliJ IDEA (recommended alongside with Cursive plugin) requires a licnese which is aquired from IT (a ticket must be created and it takes several days to arrive). You need to do few things in order to run the front-end and back-end.
2. First create a new Clojure Deps project from mnt-teet folder (Open the 
   folder without creating the project from scratch will work, too).
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