#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <signal.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <limits.h>
#include <malloc.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>

char NETWORK[20];
char GATEWAY[20];

const int READ_BUF_SIZE = 50;

FILE *log = NULL;

int kill_processes_by_name(int parameter, const char* processName) {
	int returncode = 0;

	DIR *dir = NULL;
	struct dirent *next;

	// open /proc
	dir = opendir("/proc");
	if (!dir)
		fprintf(stderr, "Can't open /proc \n");

	while ((next = readdir(dir)) != NULL) {
		FILE *status = NULL;
		char filename[READ_BUF_SIZE];
		char buffer[READ_BUF_SIZE];
		char name[READ_BUF_SIZE];

		/* Must skip ".." since that is outside /proc */
		if (strcmp(next->d_name, "..") == 0)
			continue;

		sprintf(filename, "/proc/%s/status", next->d_name);
		if (! (status = fopen(filename, "r")) ) {
			continue;
		}
		if (fgets(buffer, READ_BUF_SIZE-1, status) == NULL) {
			fclose(status);
			continue;
		}
		fclose(status);

		/* Buffer should contain a string like "Name:   binary_name" */
		sscanf(buffer, "%*s %s", name);

		if ((strstr(name, processName)) != NULL) {
			// Trying to kill
			int signal = kill(strtol(next->d_name, NULL, 0), parameter);
			if (signal != 0) {
				fprintf(stderr, "Unable to kill process %s (%s)\n",name, next->d_name);
				returncode = -1;
			}
		}
	}
	closedir(dir);
	return returncode;
}

int kill_processes_by_name(const char* processName) {
	// First try to kill with -2
	kill_processes_by_name(2, processName);
	// To make sure process is killed do it with -9
	kill_processes_by_name(9, processName);
	return 0;
}


int file_exists(const char* fileName) {
	FILE *file = NULL;
	if (! (file = fopen(fileName, "r")) ) {
		return -1;
	}
	return 0;
}

int file_unlink(const char* fileName) {
	if (file_exists(fileName) == 0) {
		if(unlink(fileName) != 0) {
			return 0;
		}
	}
	return -1;
}

void writelog(int status, const char* message) {
	time_t time_now;
    time(&time_now);
	fprintf(log,"<div class=\"date\">%s</div><div class=\"action\">%s...</div><div class=\"output\">",asctime(localtime(&time_now)),message);
	if (status == 0) {
		fprintf(log,"</div><div class=\"done\">done</div><hr>");
	}
	else {
		fprintf(log,"</div><div class=\"failed\">failed</div><hr>");
	}
}

char* chomp (char* s) {
  int end = strlen(s) - 1;
  if (end >= 0 && s[end] == '\n')
    s[end] = '\0';
  return s;
}

void stopint() {
	// Shutting usb network interface
	writelog(system("ifconfig usb0 down"),(char *)"Shutting down network interface");
	writelog(system("echo 0 > /sys/devices/virtual/net/usb0/enable"),(char *)"USB interface disabled");
}

void startint() {
    // Configuring network interface
	writelog(system("echo 1 > /sys/devices/virtual/net/usb0/enable"),(char *)"USB interface enabled");
	
	char command[100];
	sprintf(command, "ifconfig usb0 %s netmask 255.255.255.0", GATEWAY);
	int returncode = system(command);
	if (returncode == 0) {
		returncode = system("ifconfig usb0 up");
	}
	writelog(returncode,(char *)"Configuring network interface");
}

void stopipt() {
    // Tearing down firewall rules
	int returncode = system("/data/data/android.tether.usb/bin/iptables -F");
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -t nat -F");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -X");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -t nat -X");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -P FORWARD ACCEPT");
	}
	writelog(returncode,(char *)"Tearing down firewall rules");
}

void startipt() {
	// Setting up firewall rules
	char command[200];
	int returncode = system("/data/data/android.tether.usb/bin/iptables -F");
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -F -t nat");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -I FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT");
	}
	if (returncode == 0) {
		sprintf(command, "/data/data/android.tether.usb/bin/iptables -I FORWARD -s %s/24 -j ACCEPT", NETWORK);
		returncode = system(command);
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether.usb/bin/iptables -P FORWARD DROP");
	}
	if (returncode == 0) {
		sprintf(command, "/data/data/android.tether.usb/bin/iptables -t nat -I POSTROUTING -s %s/24 -j MASQUERADE", NETWORK);
		returncode = system(command);
	}
	writelog(returncode,(char *)"Setting up firewall rules");
}

void stopipfw() {
    // Disabling IP forwarding
	writelog(system("echo 0 > /proc/sys/net/ipv4/ip_forward"),(char *)"Disabling IP forwarding");
}

void startipfw() {
    // Enabling IP forwarding
	writelog(system("echo 1 > /proc/sys/net/ipv4/ip_forward"),(char *)"Enabling IP forwarding");
}

void stopdnsmasq() {
    // Stopping dnsmasq
	writelog(kill_processes_by_name((char *)"dnsmasq"),(char *)"Stopping dnsmasq");
	if (file_exists((char*)"/data/data/android.tether.usb/var/dnsmasq.pid") == 0) {
		file_unlink((char*)"/data/data/android.tether.usb/var/dnsmasq.pid");
	}
	if (file_exists((char*)"/data/data/android.tether.usb/var/dnsmasq.leases") == 0) {
		file_unlink((char*)"/data/data/android.tether.usb/var/dnsmasq.leases");
	}
}

void startdnsmasq() {
    // Starting dnsmasq
	writelog(system("/data/data/android.tether.usb/bin/dnsmasq --resolv-file=/data/data/android.tether.usb/conf/resolv.conf --conf-file=/data/data/android.tether.usb/conf/dnsmasq.conf"),(char*)"Starting dnsmasq");
}

void readlanconfig() {
	if (file_exists((char*)"/data/data/android.tether.usb/conf/lan_network.conf") == 0) {
		FILE *lanconf;
		char buffer[40];
		char name[20];
		char value[20];
		if (!(lanconf = fopen("/data/data/android.tether.usb/conf/lan_network.conf", "r")) ) {
			fprintf(stderr, "Can't open /data/data/android.tether.usb/conf/lan_network.conf for read \n");
			return;
		}
		while(fgets(buffer, sizeof(buffer), lanconf)) {
			sprintf(name,chomp(strtok(buffer, "=")));
			sprintf(value,chomp(strtok(NULL, "=")));
			if ((strstr(name, "network")) != NULL) {
				sprintf(NETWORK,value);
			}
			else if ((strstr(name, "gateway")) != NULL) {
				sprintf(GATEWAY,value);
			}
		}
	}
	else {
		sprintf(NETWORK,"192.168.2.0");
		sprintf(GATEWAY,"192.168.2.254");
	}
}

int main(int argc, char *argv[]) {
	if (argc != 2) {
		fprintf(stderr, "Usage: tether <start|stop>\n");
		return -1;
	}

	// Reading config-files
	readlanconfig();

	// Remove old Logfile
	file_unlink((char*)"/data/data/android.tether.usb/var/tether.log");

	// Open Logfile
	log = fopen ("/data/data/android.tether.usb/var/tether.log","w");

	if (strcmp(argv[1],"start") == 0) {
	 	startint();
	  	startipt();
	  	startipfw();
	  	startdnsmasq();
	}
	else if (strcmp(argv[1],"stop") == 0) {
	    stopdnsmasq();
	    stopint();
	    stopipfw();
	    stopipt();
	}

	if (log != NULL) {
		fclose (log);
	}
	return 0;
}
