#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <sys/select.h>
#include "../src/tracker/client.h"

int main(void) {
    ClientTable table;
    fd_set set;

    client_table_init(&table);
    assert(client_table_count(&table) == 0);
    assert(client_table_max_fd(&table) == -1);

    assert(client_table_add(&table, 3, "127.0.0.1", 6881) == 0);
    assert(client_table_add(&table, 5, "10.0.0.2", 6882) == 0);
    assert(client_table_add(&table, 7, "192.168.1.3", 6883) == 0);

    assert(client_table_count(&table) == 3);
    assert(client_table_max_fd(&table) == 7);

    Client *c5 = client_table_get(&table, 5);
    assert(c5 != NULL);
    assert(strcmp(c5->ip, "10.0.0.2") == 0);
    assert(c5->port == 6882);

    assert(client_table_build_fdset(&table, &set) == 7);
    assert(FD_ISSET(3, &set));
    assert(FD_ISSET(5, &set));
    assert(FD_ISSET(7, &set));
    assert(!FD_ISSET(4, &set));

    assert(client_table_add(&table, 5, "1.2.3.4", 6000) == -1);
    assert(client_table_add(&table, -1, "1.2.3.4", 6000) == -1);
    assert(client_table_add(&table, FD_SETSIZE, "1.2.3.4", 6000) == -1);

    client_table_remove(&table, 7);
    assert(client_table_count(&table) == 2);
    assert(client_table_max_fd(&table) == 5);

    client_table_remove(&table, 5);
    client_table_remove(&table, 3);
    assert(client_table_count(&table) == 0);
    assert(client_table_max_fd(&table) == -1);
    assert(client_table_get(&table, 3) == NULL);

    printf("✅ TOUS LES TESTS CLIENT REUSSIS\n\n");
    return 0;
}
