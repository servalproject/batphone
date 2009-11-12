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
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>

# define init_module(mod, len, opts) syscall(__NR_init_module, mod, len, opts)
# define delete_module(mod, flags) syscall(__NR_delete_module, mod, flags)

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
int kernel_module_loaded(const char* moduleName) {
	int module_found = -1;
	FILE *modules;
	char buffer[READ_BUF_SIZE];
	char name[READ_BUF_SIZE];

	if (! (modules = fopen("/proc/modules", "r")) ) {
		fprintf(stderr, "Can't open /proc/modules for read \n");
		return -1;
	}

	while(fgets(buffer, sizeof(buffer), modules)) {
	    /* process the line */
		sscanf(buffer, "%s %*s", name);
		if ((strstr(name, moduleName)) != NULL) {
			module_found = 0;
		}
	}
	fclose(modules);
	return module_found;
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

char* concat(char* s1, char* s2) {
	char* result = (char *)malloc((strlen(s1) + strlen(s2) + 1)*sizeof(char));
	strcpy(result, s1);
	strcat(result, s2);
	return result;
}

char* chomp (char* s) {
  int end = strlen(s) - 1;
  if (end >= 0 && s[end] == '\n')
    s[end] = '\0';
  return s;
}

static void *read_file(const char *filename, ssize_t *_size) {
        int ret, fd;
        struct stat sb;
        ssize_t size;
        void *buffer = NULL;

        /* open the file */
        fd = open(filename, O_RDONLY);
        if (fd < 0)
                return NULL;

        /* find out how big it is */
        if (fstat(fd, &sb) < 0)
                goto bail;
        size = sb.st_size;

        /* allocate memory for it to be read into */
        buffer = malloc(size);
        if (!buffer)
                goto bail;

        /* slurp it into our buffer */
        ret = read(fd, buffer, size);
        if (ret != size)
                goto bail;

        /* let the caller know how big it is */
        *_size = size;

bail:
        close(fd);
        return buffer;
}

int insmod(const char *filename, const char *options) {
        ssize_t len;
        void *image;
        int rc;

        if (!options)
                options = "";

        len = INT_MAX - 4095;
        errno = ENOMEM;
        image = read_file(filename, &len);

        if (!image)
                return -errno;

        errno = 0;
        init_module(image, len, options);
        rc = errno;
        free(image);
        return rc;
}

int rmmod(const char *modname) {
	return delete_module(modname, O_NONBLOCK | O_EXCL);
}

void stopwifi() {
	// Deactivating Wifi-Encryption
	kill_processes_by_name((char *)"wpa_supplicant");
	// Killing dnsmasq
	kill_processes_by_name((char *)"dnsmasq");
	// Loading wlan-kernel-module
	if (kernel_module_loaded((char *)"wlan") == 0) {
		writelog(rmmod("wlan"),(char *)"Unloading wlan.ko module");
	}
}

void startwifi() {
	stopwifi();

	// Loading wlan-kernel-module
	if (kernel_module_loaded((char *)"wlan") != 0) {
		writelog(insmod("/system/lib/modules/wlan.ko",""),(char *)"Loading wlan.ko module");
	}
	// Configuring WiFi interface
	writelog(system("wlan_loader -f /system/etc/wifi/Fw1251r1c.bin -e /proc/calibration -i /data/data/android.tether/conf/tiwlan.ini"),
			(char *)"Configuring WiFi interface");
	// Activating Wifi-Encryption
	if (file_exists((char *)"/data/data/android.tether/conf/wpa_supplicant.conf") == 0) {
		writelog(system("cd /data/local/tmp;wpa_supplicant -B -Dtiwlan0 -itiwlan0 -c/data/data/android.tether/conf/wpa_supplicant.conf"),
			(char *)"Activating Wifi encryption");
	}
}

void stoppand() {
	// Stopping pand
	system("/data/data/android.tether/bin/pand -K");
	writelog(kill_processes_by_name((char *)"pand"),(char *)"Stopping pand");
	if (file_exists((char*)"/data/data/android.tether/var/pand.pid") == 0) {
		file_unlink((char*)"/data/data/android.tether/var/pand.pid");
	}
}

void startpand() {
	// Starting pand
	writelog(system("/data/data/android.tether/bin/pand --listen --role NAP --devup /data/data/android.tether/bin/blue-up.sh --devdown /data/data/android.tether/bin/blue-down.sh --pidfile /data/data/android.tether/var/pand.pid"),
			(char *)"Starting pand");
}

void stopint() {
	// Shutting down network interface
	if (kernel_module_loaded((char *)"wlan") == 0) {
		writelog(system("ifconfig tiwlan0 down"),(char *)"Shutting down network interface");
	}
}

void startint() {
    // Configuring network interface
	if (kernel_module_loaded((char *)"wlan") == 0) {
		char command[100];
		sprintf(command, "ifconfig tiwlan0 %s netmask 255.255.255.0", GATEWAY);
		int returncode = system(command);
		if (returncode == 0) {
			returncode = system("ifconfig tiwlan0 up");
		}
		writelog(returncode,(char *)"Configuring network interface");
	}
}

void stopipt() {
    // Tearing down firewall rules
	int returncode = system("/data/data/android.tether/bin/iptables -F");
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -t nat -F");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -X");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -t nat -X");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -P FORWARD ACCEPT");
	}
	writelog(returncode,(char *)"Tearing down firewall rules");
}

void startipt() {
	// Setting up firewall rules
	char command[100];
	int returncode = system("/data/data/android.tether/bin/iptables -F");
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -F -t nat");
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -I FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT");
	}
	if (returncode == 0) {
		sprintf(command, "/data/data/android.tether/bin/iptables -I FORWARD -s %s/24 -j ACCEPT", NETWORK);
		returncode = system(command);
	}
	if (returncode == 0) {
		returncode = system("/data/data/android.tether/bin/iptables -P FORWARD DROP");
	}
	if (returncode == 0) {
		sprintf(command, "/data/data/android.tether/bin/iptables -t nat -I POSTROUTING -s %s/24 -j MASQUERADE", NETWORK);
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
	if (file_exists((char*)"/data/data/android.tether/var/dnsmasq.pid") == 0) {
		file_unlink((char*)"/data/data/android.tether/var/dnsmasq.pid");
	}
	if (file_exists((char*)"/data/data/android.tether/var/dnsmasq.leases") == 0) {
		file_unlink((char*)"/data/data/android.tether/var/dnsmasq.leases");
	}
}

void startdnsmasq() {
    // Starting dnsmasq
	writelog(system("/data/data/android.tether/bin/dnsmasq --resolv-file=/data/data/android.tether/conf/resolv.conf --conf-file=/data/data/android.tether/conf/dnsmasq.conf"),(char*)"Starting dnsmasq");
}

void startsecnat() {
    // Activating MAC access control
	if (file_exists((char*)"/data/data/android.tether/conf/whitelist_mac.conf") == 0) {
		char command[100];
		sprintf(command, "/data/data/android.tether/bin/iptables -t nat -I PREROUTING -s %s/24 -j DROP", NETWORK);
		writelog(system(command),(char*)"Activating MAC access control");
	}
}

void startmacwhitelist() {
    // Adding MAC addresses for allowed clients
	if (file_exists((char*)"/data/data/android.tether/conf/whitelist_mac.conf") == 0) {
		FILE *macs;
		char buffer[20];
		char command[100];
		if (! (macs = fopen("/data/data/android.tether/conf/whitelist_mac.conf", "r")) ) {
			fprintf(stderr, "Can't open /data/data/android.tether/conf/whitelist_mac.conf for read \n");
			return;
		}
		int returncode = 0;
		while(fgets(buffer, sizeof(buffer), macs) && returncode == 0) {
		    /* process the line */
			sscanf(buffer, "%s", buffer);
			sprintf(command,"/data/data/android.tether/bin/iptables -t nat -I PREROUTING -m mac --mac-source %s -j ACCEPT", buffer);
			//fprintf(stdout, "Enabling whitelist for: %s \n", command);
			returncode = system(command);

		}
		writelog(returncode,(char*)"Adding MAC addresses for allowed clients");
		fclose(macs);
	}
}

void readlanconfig() {
	if (file_exists((char*)"/data/data/android.tether/conf/lan_network.conf") == 0) {
		FILE *lanconf;
		char buffer[100];
		char name[50];
		char value[50];
		if (!(lanconf = fopen("/data/data/android.tether/conf/lan_network.conf", "r")) ) {
			fprintf(stderr, "Can't open /data/data/android.tether/conf/lan_network.conf for read \n");
			return;
		}
		while(fgets(buffer, sizeof(buffer), lanconf)) {
			sprintf(name,chomp(strtok(buffer, "=")));
			sprintf(value,chomp(strtok(NULL, "=")));
			if ((strstr(name, "network")) != NULL) {
				//chomp(value);
				sprintf(NETWORK,value);
			}
			else if ((strstr(name, "gateway")) != NULL) {
				//chomp(value);
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
	readlanconfig();
	if (argc != 2) {
		fprintf(stderr, "Usage: tether <start|stop|startbt|stopbt>\n");
		return -1;
	}
	// Remove old Logfile
	file_unlink((char*)"/data/data/android.tether/var/tether.log");

	// Open Logfile
	log = fopen ("/data/data/android.tether/var/tether.log","w");

	if (strcmp(argv[1],"start") == 0) {
		startwifi();
	 	startint();
	  	startipt();
	  	startipfw();
	  	startdnsmasq();
	  	startsecnat();
	  	startmacwhitelist();
	}
	else if (strcmp(argv[1],"stop") == 0) {
	    stopdnsmasq();
	    stopint();
	    stopwifi();
	    stopipfw();
	    stopipt();
	}
	else if (strcmp(argv[1],"startbt") == 0) {
	  	startpand();
	  	startipt();
	  	startipfw();
	    startsecnat();
	    startmacwhitelist();
	}
	else if (strcmp(argv[1],"stopbt") == 0) {
	    stopdnsmasq();
	    stoppand();
	    stopipfw();
	    stopipt();
	}
	else if (strcmp(argv[1],"restartsecwifi") == 0) {
	    stopipt();
	    startipt();
	    startsecnat();
	    startmacwhitelist();
	}

	if (log != NULL) {
		fclose (log);
	}
	return 0;
}
