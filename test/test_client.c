#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <sys/select.h>
#include "../src/tracker/client.h"

int main(void) {
    fd_set set;

    client_system_init();
    assert(client_max_fd() == -1);

    assert(create_client(3, "127.0.0.1", 6881) != NULL);
    assert(create_client(5, "10.0.0.2", 6882) != NULL);
    assert(create_client(7, "192.168.1.3", 6883) != NULL);

    assert(client_max_fd() == 7);

    Client *c5 = get_client(5);
    assert(c5 != NULL);
    assert(strcmp(client_get_ip(c5), "10.0.0.2") == 0);

    assert(create_client(5, "1.2.3.4", 6000) == NULL);
    assert(create_client(-1, "1.2.3.4", 6000) == NULL);
    assert(create_client(FD_SETSIZE, "1.2.3.4", 6000) == NULL);

    remove_client(7);
    assert(client_max_fd() == 5);

    remove_client(5);
    remove_client(3);
    assert(client_max_fd() == -1);
    assert(get_client(3) == NULL);

    printf("✅ TOUS LES TESTS CLIENT REUSSIS\n\n");
    return 0;
}
