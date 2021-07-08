# Build and deployment scripts for AWS CodeBuild

# Instuctions for using install-standalone-vm.bash script from CodeBuild

1. Edit deploy-standalone-vm job buildspec in dev env aws codebuild console to refer to your ssh key (optional, if you want ssh access to the vm)
2. select "run with parameters" and select the branch you want to deploy, and put a MACHINE_ID variable in the environment having value a, b, c, d, or e.
3. Start job, takes around 30-40 mins because of slow postgresql backup/restore. You can ssh in and tail -f /var/log/syslog file to follow progress

(you can also run this from dev laptop if you have awscli setup).
