# Todolist For Version 0.1

### Task Runner
- Used to download and run builds from builds
- [ ] Download build environments
  - [ ] From URL
  - [ ] From File System
- [ ] Run command list post download

### Task Manager
- Manages the tasks (spin up and spin down on certain events)
- [ ] Run all tasks from docker images as containers
- [ ] Build map
  - [ ] Map of all builds in the builds directory
  - [ ] List of builds marked as "failures"
- [ ] On start
  - [ ] Load build "map" from file system
- [ ] On task start
  - [ ] Download code
    - [ ] Either zip file or single file
    - [ ] From url or file system
    - [ ] Save any new or modified zip files to own file system directory
    - [ ] If build is marked "failure" step back down build list for this entry until a success is found
  - [ ] Build docker image with run commands
  - [ ] Add arg to microservices to set ID and use that
  - [ ] Run docker image
- [ ] Create task of type from request
- [ ] Per task type config
  - [ ] Minimum number of tasks per type running at a time
  - [ ] Maximum tasks "waiting for start"
  - [ ] Startup wait time until new task is created (default 1 minute)
  - [ ] Flag: On fail, mark build dead