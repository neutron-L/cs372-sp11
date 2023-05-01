#include <stdio.h>
#include <stdlib.h>
 #include <ctype.h>

int fact(int num)
{
	if (num == 1)
		return num;
	return num * fact(num - 1);
}

int translate(const char * s)
{
	int num = 0;
	
	int i = 0;
	while (s[i] && isdigit(s[i]))
	{
		num = num * 10 + (s[i] - '0');
		++i;
	}
	if (s[i])
		return -1;
	else
		return num;
}

int main(int argc, char ** argv)
{
	// check argument
	int num;

	if (argc < 2 || (num = translate(argv[1])) == -1)
		printf("Huh?\n");
	else if (num > 12)
		printf("Overflow\n");
	else
		printf("%d\n", fact(num));
	return 0;
}
