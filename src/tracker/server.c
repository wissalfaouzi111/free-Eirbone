#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include "message.h" 
#include "file.h"
#include "client.h"
#define MAX_OUTPUT 1024

// pour gerer le ctrl+C et liberer le port
#include <signal.h>  
int sockfd = -1;

void handle_sigint(int sig) {
    (void)sig;
    printf("\nArrêt : Ctrl+C détecté \n");
    if (sockfd >= 0) {
        close(sockfd);
    }
    exit(0);
}

void error(char *msg){
    perror(msg);
    exit(1);
}

void announce(MessageP2P *msg, Client *client) {
    if (msg == NULL) return;
    for (int i = 0; i < msg->nb_seed; i++) {
        FichierInfo *fichier = creer_fichier_info(msg->liste_seed[i].nom, msg->liste_seed[i].taille, msg->liste_seed[i].taille_piece, msg->liste_seed[i].cle_md5);
        if (fichier != NULL) {
            Peer *peer = creer_peer(client_get_ip(client), msg->port);
            if (peer != NULL) {
                add_peer_to_file(fichier->cle_md5, peer);
            }
        }
    }
}

char* look(MessageP2P *msg) {
    if (msg == NULL || msg->commande[0] == '\0') return NULL;

    char *output = malloc(MAX_OUTPUT);
    if (output == NULL) return NULL;

    snprintf(output, MAX_OUTPUT, "list [");
    
    if (strlen(msg->hash_cible) > 0) {
        FichierInfo *fichier = trouver_fichier_par_nom(msg->hash_cible);
        if (fichier != NULL) {
            size_t len = strlen(output);
            snprintf(output + len, MAX_OUTPUT - len, " %s %ld %d %s", 
                    fichier->nom, fichier->taille, fichier->taille_piece, fichier->cle_md5);
        }
    }
    strcat(output, " ]\n");
    return output;
}

void update_tracker(MessageP2P *msg, Client *client) {
    if (msg == NULL) return;
    
    Peer *peer = trouver_peer_par_ip(client_get_ip(client));
    if (peer == NULL) {
        // Fallback si le pair fait un update avant un announce
        peer = creer_peer(client_get_ip(client), 6881);
    }
    
    for (int i = 0; i < msg->nb_seed; i++) {
        add_peer_to_file(msg->liste_seed_keys[i], peer);
    }
    
    for (int i = 0; i < msg->nb_leech; i++) {
        add_peer_to_file(msg->liste_leech_keys[i], peer);
    }
}

int answer(MessageP2P *msg, char *response, Client *client) {
    int is_error = 0;
    if (msg == NULL || msg->commande[0] == '\0') {
        sprintf(response, "ERROR Invalid format\n");
        is_error = 1;
    }
    else if (strcmp(msg->commande, "announce") == 0) {
        announce(msg, client);
        sprintf(response, "ok\n");
    }
    else if (strcmp(msg->commande, "update") == 0) {
        update_tracker(msg, client);
        sprintf(response, "ok\n"); 
    }
    else if (strcmp(msg->commande, "look") == 0) {
        char* output = look(msg);
        if (output == NULL) {
            sprintf(response, "list [ ]\n");
        } else {
            strncpy(response, output, MAX_OUTPUT - 1);
            response[MAX_OUTPUT - 1] = '\0';
            free(output);
        }
    }
    else if (strcmp(msg->commande, "getfile") == 0) {
        char *peers_str = generer_liste_peers_pour_fichier(msg->hash_cible);
        if (peers_str != NULL) {
            snprintf(response, MAX_OUTPUT, "peers %s %s\n", msg->hash_cible, peers_str);
            free(peers_str);
        } else {
            snprintf(response, MAX_OUTPUT, "peers %s [ ]\n", msg->hash_cible);
        }
    }
    else {
        sprintf(response, "ERROR Unknown command\n");
        is_error = 1;
    }
    return is_error;
}

static void close_client_connection(int client_fd, fd_set *master_set) {
    remove_client(client_fd);
    close(client_fd);
    FD_CLR(client_fd, master_set);
}

static void accept_new_client(fd_set *master_set) {
    int newsockfd;
    socklen_t cli_socket_len;
    struct sockaddr_in cli_addr;

    cli_socket_len = sizeof(cli_addr);
    newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &cli_socket_len);
    if (newsockfd < 0) {
        return;
    }

    if (create_client(newsockfd, inet_ntoa(cli_addr.sin_addr), ntohs(cli_addr.sin_port)) == NULL) {
        close(newsockfd);
        return;
    }

    FD_SET(newsockfd, master_set);
    printf("Connexion acceptée de %s\n", client_get_ip(get_client(newsockfd)));
}

static void handle_ready_client(int client_fd, fd_set *master_set) {
    char buffer[BUFLEN];
    char response[MAX_OUTPUT];
    int n;
    int should_close_after_send = 0;

    Client *client = get_client(client_fd);
    if (client == NULL) {
        close(client_fd);
        FD_CLR(client_fd, master_set);
        return;
    }

    // On traite un seul message par passage select pour ne pas bloquer les autres clients.
    bzero(buffer, BUFLEN);
    n = read(client_fd, buffer, BUFLEN - 1);

    if (n <= 0) {
        printf("Le client %s s'est déconnecté.\n", client_get_ip(client));
        close_client_connection(client_fd, master_set);
        return;
    }

    printf("[%s] Recu (%d octets): %.*s\n", client_get_ip(client), n, n, buffer);

    MessageP2P *msg = add_message(buffer);
    if (msg != NULL && msg->commande[0] != '\0') {
        printf("[%s] Commande detectee: %s\n", client_get_ip(client), msg->commande);
    } else {
        printf("[%s] Commande detectee: <invalide>\n", client_get_ip(client));
    }

    bzero(response, BUFLEN);
    int is_error = answer(msg, response, client);

    if (is_error) {
        int count = client_inc_error_count(client);
        printf("Erreur de formatage (%d/3)\n", count);
        if (count >= 3) {
            printf("Déconnexion forcée de %s : 3 erreurs successives.\n", client_get_ip(client));
            should_close_after_send = 1;
        }
    } else {
        client_reset_error_count(client);
    }

    printf("[%s] Reponse envoyee: %s", client_get_ip(client), response);
    write(client_fd, response, strlen(response));

    if (msg != NULL) {
        free(msg);
    }

    if (should_close_after_send) {
        close_client_connection(client_fd, master_set);
    }
}



int main(int argc, char *argv[]){
    int portno;
    int current_max_fd;
    int max_fd;
    fd_set master_set, read_set;
    struct sockaddr_in serv_addr;

    client_system_init();

    if (argc < 2) {
        fprintf(stderr,"usage: %s <port>\n", argv[0]);
        exit(1);
    }
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    if (sockfd < 0) error("ERROR opening socket");

    signal(SIGINT, handle_sigint);

    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        error("ERROR setsockopt");
    }
    
    bzero((char *) &serv_addr, sizeof(serv_addr));

    portno = atoi(argv[1]);

    serv_addr.sin_family = AF_INET;

    inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr.s_addr);
    serv_addr.sin_port = htons(portno);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) error("ERROR on bind()");


    printf("=> Serveur en écoute sur le port %d...\n", portno);
    listen(sockfd, 5);

    FD_ZERO(&master_set);
    FD_SET(sockfd, &master_set);
    max_fd = sockfd;

    while (1) {
        read_set = master_set;

        current_max_fd = client_max_fd();
        max_fd = sockfd;
        if (current_max_fd > max_fd) {
            max_fd = current_max_fd;
        }

        if (select(max_fd + 1, &read_set, NULL, NULL, NULL) < 0) {
            continue;
        }

        if (FD_ISSET(sockfd, &read_set)) {
            accept_new_client(&master_set);
        }

        for (int fd = 0; fd <= max_fd; fd++) {
            if (fd == sockfd) {
                continue;
            }
            if (FD_ISSET(fd, &read_set)) {
                handle_ready_client(fd, &master_set);
            }
        }
    }
    close(sockfd);

    return 0; 
}
