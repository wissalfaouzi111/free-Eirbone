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

void announce(MessageP2P *msg, char *client_ip) {
    if (msg == NULL) return;
    for (int i = 0; i < msg->nb_seed; i++) {
        FichierInfo *fichier = creer_fichier_info(msg->liste_seed[i].nom, msg->liste_seed[i].taille, msg->liste_seed[i].taille_piece, msg->liste_seed[i].cle_md5);
        if (fichier != NULL) {
            Peer *peer = creer_peer(client_ip, msg->port); 
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

void update_tracker(MessageP2P *msg, char *client_ip) {
    if (msg == NULL) return;
    
    Peer *peer = trouver_peer_par_ip(client_ip);
    if (peer == NULL) {
        // Fallback si le pair fait un update avant un announce
        peer = creer_peer(client_ip, 6881); 
    }
    
    for (int i = 0; i < msg->nb_seed; i++) {
        add_peer_to_file(msg->liste_seed_keys[i], peer);
    }
    
    for (int i = 0; i < msg->nb_leech; i++) {
        add_peer_to_file(msg->liste_leech_keys[i], peer);
    }
}

int answer(MessageP2P *msg, char *response, char *client_ip) {
    int is_error = 0;
    if (msg == NULL || msg->commande[0] == '\0') {
        sprintf(response, "ERROR Invalid format\n");
        is_error = 1;
    }
    else if (strcmp(msg->commande, "announce") == 0) {
        announce(msg, client_ip);
        sprintf(response, "ok\n");
    }
    else if (strcmp(msg->commande, "update") == 0) {
        update_tracker(msg, client_ip);
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

void handle_client(int newsockfd, struct sockaddr_in *cli_addr) {
    char buffer[BUFLEN];
    char response[MAX_OUTPUT];
    int error_count = 0;
    int n;
    
    char *client_ip = inet_ntoa(cli_addr->sin_addr);
    printf("Connexion acceptée de %s\n", client_ip);

    // Keep-alive et coupure après 3 erreurs
    while (error_count < 3) {
        bzero(buffer, BUFLEN);

        // printf("=> read() ? ...");
        // getchar(); 
        // printf("OK\n");

        n = read(newsockfd, buffer, BUFLEN - 1); //on a modifié la taille de buffer lu(10)
        // if (n < 0) {
        //     error("ERROR reading from socket");
        //     close(newsockfd);
        //     continue; // Passer à la prochaine connexion
        // }
        
        if (n <= 0) {
            printf("Le client %s s'est déconnecté.\n", client_ip);
            break; // Déconnexion propre ou erreur réseau
        }
        // printf("Here is the message: (size: %d bytes) %s\n", n, buffer);

        // printf("=> write() ? ...");
        // getchar(); 
        // printf("OK\n");

        // n = write(newsockfd, "I got your message",18);
        // if (n < 0) error("ERROR writing to socket");
        
        MessageP2P *msg = add_message(buffer);
        bzero(response, BUFLEN);
        int is_error = 0;

        is_error = answer(msg, response, client_ip);

        if (is_error) {
            error_count++;
            printf("Erreur de formatage (%d/3)\n", error_count);
        } else {
            error_count = 0; // Remise à zéro si message valide
        }

        write(newsockfd, response, strlen(response));
        if (msg != NULL) free(msg);
    }
    
    if (error_count >= 3) {
        printf("Déconnexion forcée de %s : 3 erreurs successives.\n", client_ip);
    }
    close(newsockfd);
}



int main(int argc, char *argv[]){
    int newsockfd, portno;
    socklen_t cli_socket_len;
    struct sockaddr_in serv_addr, cli_addr;

    if (argc < 2) {
        fprintf(stderr,"usage: %s <port>\n", argv[0]);
        exit(1);
    }
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    if (sockfd < 0) error("ERROR opening socket");


    // Intercepter le Ctrl+C 
    signal(SIGINT, handle_sigint);

    // Forcer la réutilisation du port 
    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        error("ERROR setsockopt");
    }
    
    bzero((char *) &serv_addr, sizeof(serv_addr));

    portno = atoi(argv[1]);

    serv_addr.sin_family = AF_INET;
    //serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr.s_addr);// On a remplacer 127.0.0.1 par 0.0.0.0 pour que le serveur puisse accepter les connexions de n'importe quelle adresse IP
    serv_addr.sin_port = htons(portno);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) error("ERROR on bind()");

    // printf("=> listen() ? ...");
    // getchar(); 
    // printf("OK\n");

    printf("=> Serveur en écoute sur le port %d...\n", portno);
    listen(sockfd, 5);

    // printf("=> accept() ? ...");
    // getchar(); 
    // printf("OK\n");

    while (1) { // Boucle pour accepter plusieurs connexions successives
        cli_socket_len = sizeof(cli_addr);
        newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &cli_socket_len);
        if (newsockfd < 0) continue;

        handle_client(newsockfd, &cli_addr);
    }
    close(sockfd);

    return 0; 
}
