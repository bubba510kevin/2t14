#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>

#define PORT 5000
#define BUFFER_SIZE 8192
#define MAX_CLIENTS 50

typedef struct {
    char ip[32];
    char name[64];
    char command[256];
    char response[2048];
} ClientCommand;

ClientCommand clients[MAX_CLIENTS];
int client_count = 0;
pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

// ------------------- Helpers -------------------
ClientCommand* get_client(const char* ip) {
    for (int i = 0; i < client_count; i++) {
        if (strcmp(clients[i].ip, ip) == 0) {
            return &clients[i];
        }
    }
    return NULL;
}

ClientCommand* add_client(const char* ip, const char* name) {
    ClientCommand* c = get_client(ip);
    if (c) return c;

    if (client_count < MAX_CLIENTS) {
        strncpy(clients[client_count].ip, ip, sizeof(clients[client_count].ip)-1);
        strncpy(clients[client_count].name, name, sizeof(clients[client_count].name)-1);
        clients[client_count].command[0] = '\0';
        clients[client_count].response[0] = '\0';
        client_count++;
        return &clients[client_count - 1];
    }
    return NULL;
}

void json_escape(const char* input, char* output, size_t len) {
    size_t j = 0;
    for (size_t i = 0; input[i] != '\0' && j < len - 2; i++) {
        if (input[i] == '"' || input[i] == '\\') {
            if (j < len - 3) {
                output[j++] = '\\';
                output[j++] = input[i];
            }
        } else if (input[i] == '\n') {
            if (j < len - 3) {
                output[j++] = '\\';
                output[j++] = 'n';
            }
        } else {
            output[j++] = input[i];
        }
    }
    output[j] = '\0';
}

// ------------------- Request Handling -------------------
void handle_request(int client_sock, char* buffer) {
    char method[8], path[128], body[BUFFER_SIZE];
    method[0] = path[0] = body[0] = '\0';

    sscanf(buffer, "%7s %127s", method, path);

    char* body_ptr = strstr(buffer, "\r\n\r\n");
    if (body_ptr) strncpy(body, body_ptr + 4, sizeof(body)-1);

    pthread_mutex_lock(&lock);

    // Register PC2
    if (strcmp(path, "/register_pc2") == 0 && strcmp(method, "POST") == 0) {
        char ip[32], name[64];
        if (sscanf(body, "{ \"ip\": \"%31[^\"]\", \"name\": \"%63[^\"]\" }", ip, name) == 2) {
            add_client(ip, name);
            char response[] = "HTTP/1.1 200 OK\r\nContent-Length: 21\r\n\r\n{\"status\":\"ok\"}";
            send(client_sock, response, strlen(response), 0);
        }
        pthread_mutex_unlock(&lock);
        return;
    }

    // List PC2s
    if (strcmp(path, "/list_pc2s") == 0 && strcmp(method, "GET") == 0) {
        char json[BUFFER_SIZE];
        strcpy(json, "{");
        for (int i = 0; i < client_count; i++) {
            char entry[256];
            snprintf(entry, sizeof(entry), "\"%s\":\"%s\"%s",
                     clients[i].name, clients[i].ip,
                     (i < client_count - 1) ? "," : "");
            strcat(json, entry);
        }
        strcat(json, "}");
        char response[BUFFER_SIZE];
        snprintf(response, sizeof(response),
                 "HTTP/1.1 200 OK\r\nContent-Length: %zu\r\n\r\n%s",
                 strlen(json), json);
        send(client_sock, response, strlen(response), 0);
        pthread_mutex_unlock(&lock);
        return;
    }

    // Send command
    if (strcmp(path, "/send_command") == 0 && strcmp(method, "POST") == 0) {
        char target[32], command[256];
        if (sscanf(body, "{ \"target\": \"%31[^\"]\", \"command\": \"%255[^\"]\" }",
                   target, command) == 2) {
            ClientCommand* c = get_client(target);
            if (!c) c = add_client(target, target);
            if (c) {
                strncpy(c->command, command, sizeof(c->command)-1);
                char response[] = "HTTP/1.1 200 OK\r\nContent-Length: 27\r\n\r\n{\"status\":\"command stored\"}";
                send(client_sock, response, strlen(response), 0);
            }
        }
        pthread_mutex_unlock(&lock);
        return;
    }

    // Get command
    if (strncmp(path, "/get_command/", 13) == 0 && strcmp(method, "GET") == 0) {
        const char* ip = path + 13;
        ClientCommand* c = get_client(ip);
        char response[BUFFER_SIZE];
        if (c && strlen(c->command) > 0) {
            char escaped[512];
            json_escape(c->command, escaped, sizeof(escaped));
            snprintf(response, sizeof(response),
                     "HTTP/1.1 200 OK\r\nContent-Length: %zu\r\n\r\n{\"command\":\"%s\"}",
                     strlen(escaped)+12, escaped);
            c->command[0] = '\0';
        } else {
            snprintf(response, sizeof(response),
                     "HTTP/1.1 200 OK\r\nContent-Length: 21\r\n\r\n{\"command\":null}");
        }
        send(client_sock, response, strlen(response), 0);
        pthread_mutex_unlock(&lock);
        return;
    }

    // Send response
    if (strcmp(path, "/send_response") == 0 && strcmp(method, "POST") == 0) {
        char sender[32], output[2048];
        if (sscanf(body, "{ \"sender\": \"%31[^\"]\", \"output\": \"%2047[^\"]\" }",
                   sender, output) == 2) {
            ClientCommand* c = get_client(sender);
            if (c) {
                strncpy(c->response, output, sizeof(c->response)-1);
                char response[] = "HTTP/1.1 200 OK\r\nContent-Length: 23\r\n\r\n{\"status\":\"stored\"}";
                send(client_sock, response, strlen(response), 0);
            }
        }
        pthread_mutex_unlock(&lock);
        return;
    }

    // Get response
    if (strncmp(path, "/get_response/", 14) == 0 && strcmp(method, "GET") == 0) {
        const char* ip = path + 14;
        ClientCommand* c = get_client(ip);
        char response[BUFFER_SIZE];
        if (c && strlen(c->response) > 0) {
            char escaped[4096];
            json_escape(c->response, escaped, sizeof(escaped));
            snprintf(response, sizeof(response),
                     "HTTP/1.1 200 OK\r\nContent-Length: %zu\r\n\r\n{\"output\":\"%s\"}",
                     strlen(escaped)+11, escaped);
            c->response[0] = '\0';
        } else {
            snprintf(response, sizeof(response),
                     "HTTP/1.1 200 OK\r\nContent-Length: 17\r\n\r\n{\"output\":null}");
        }
        send(client_sock, response, strlen(response), 0);
        pthread_mutex_unlock(&lock);
        return;
    }

    // Not Found
    char response[] = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found";
    send(client_sock, response, strlen(response), 0);
    pthread_mutex_unlock(&lock);
}

int main() {
    int server_fd, client_sock;
    struct sockaddr_in address;
    int addrlen = sizeof(address);
    char buffer[BUFFER_SIZE];

    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd == 0) { perror("Socket failed"); exit(EXIT_FAILURE); }

    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);

    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) {
        perror("Bind failed"); exit(EXIT_FAILURE);
    }

    if (listen(server_fd, 10) < 0) {
        perror("Listen failed"); exit(EXIT_FAILURE);
    }

    printf("Server running on port %d...\n", PORT);

    while (1) {
        client_sock = accept(server_fd, (struct sockaddr*)&address, (socklen_t*)&addrlen);
        if (client_sock < 0) { perror("Accept failed"); continue; }

        memset(buffer, 0, sizeof(buffer));
        read(client_sock, buffer, sizeof(buffer) - 1);
        handle_request(client_sock, buffer);
        close(client_sock);
    }
    return 0;
}
