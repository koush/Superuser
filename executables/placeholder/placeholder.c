/*
** Copyright 2015, Pierre-Hugues "phhusson" Husson
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#include <limits.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>
#include <stdint.h>
#include <pwd.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <sys/types.h>
#include <selinux/selinux.h>

static void setup_selinux() {
	//This function is called as system:root context = system_server
	int fd = open("/data/security/hello", O_WRONLY|O_CREAT, 0666);
	write(fd, "hello\n", 6);
	close(fd);
}

int main(int argc, char *argv[], char *envp[]) {
	int p = fork();
	if(!p) {
		setuid(1000);
		seteuid(1000);
		setcon("u:r:system_server:s0");
		setup_selinux();
		return 0;
	}

	//Wait for it...
	int status = -1;
	waitpid(p, &status, 0);
	//TODO: Error checking ?
	

	//Exec original app_process32
	//Should be useless, because it's current context ?
	setexeccon("u:r:zygote:s0");
	execve("/system/bin/app_process32.old", argv, envp);

	return 1;
}
