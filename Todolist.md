# CICD-Builder
- The job of this service is to pull and then compile a project from a git repository
- [x] Option: Url to pull from
- [x] Option: Command(s) to run build
- [x] Option: Path (or list of paths) to builds locally "resources"
- [x] Option: Local config path
- [x] Option: Run build on startup
  - [x] If set true, run build on startup
  - [x] If set false, broadcast that the builder is ready
- [x] Endpoint: Run build (with options above and a random key from the manager)
  - [x] Send back a packet saying accepted
  - [x] If a build has already been triggered, send back a packet saying busy
- [x] On build complete
  - [x] Zip resources
  - [x] A build may only be pushed to the file system if all commands pass without errors
  - [x] Broadcast build success or fail with zip file

# CICD-Runner
- The job of this service is to pull builds from the service file system and run those builds
- [ ] Option: Path on file system to folder containing 
- [ ] Option: Command to run the build with (with placeholder for exact file name)
- [ ] Run build on start
- [ ] If build exits with error
  - [ ] Mark build bad
  - [ ] Open older build that is marked good
- [ ] If build exits without an error
  - [ ] Shutdown

# CICD-API
- This is a simple api that can be used by other services to more easily
- [ ] Find and access builds
- [ ] Trigger builds
- [ ] Create and execute runners

# CICD-Manager
- The job of this service is to detect when a build needs to be run and then run that build
- [ ] Endpoint: Run build now (takes a name that references the build configurations)
  - [ ] Send back build key
- [ ] Endpoint: Get all build possibilities
- [ ] On build trigger
  - [ ] Spin up CICD-Builder
  - [ ] Use docker image of builder once builder is working right
  - [ ] Add build to queue
- [ ] When a builder broadcasts ready
  - [ ] Calls its run build endpoint
    - [ ] Immediately remove the build from the queue
    - [ ] If returns accepted, great
    - [ ] If returns busy, add the build back into the queue
- [ ] Configuration for build targets (build configuration contains a list of these)
  - [ ] Path to secrets
  - [ ] Url to pull from
  - [ ] Command(s) to run build
  - [ ] Path to get final builds from