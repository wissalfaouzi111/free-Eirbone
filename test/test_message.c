#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "message.h"

static int test_add_message_announce_commande(void)
{
	char input[] = "announce litsen 4242";
	MessageP2P *msg = add_message(input);

	if (msg == NULL)
	{
		printf("[FAIL] add_message announce: returned NULL\n");
		return 1;
	}

	if (strcmp(msg->commande, "announce") != 0)
	{
		printf("[FAIL] add_message announce: commande='%s'\n", msg->commande);
		free(msg);
		return 1;
	}

	printf("[PASS] add_message announce: commande OK\n");
	free(msg);
	return 0;
}

static int test_add_message_other_commande(void)
{
	char input[] = "look abc";
	MessageP2P *msg = add_message(input);

	if (msg == NULL)
	{
		printf("[FAIL] add_message look: returned NULL\n");
		return 1;
	}

	if (strcmp(msg->commande, "look") != 0)
	{
		printf("[FAIL] add_message look: commande='%s'\n", msg->commande);
		free(msg);
		return 1;
	}

	printf("[PASS] add_message look: commande OK\n");
	free(msg);
	return 0;
}

int main(void)
{
	int failures = 0;

	failures += test_add_message_announce_commande();
	failures += test_add_message_other_commande();

	if (failures == 0)
	{
		printf("All tests passed.\n");
		return 0;
	}

	printf("%d test(s) failed.\n", failures);
	return 1;
}
