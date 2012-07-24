SRCS=	echo.c
OBJS=   $(SRCS:.c=.o)

CC=	cc
RM=	rm

CFLAGS=	-DHAVE_CONFIG_H=1

%.o:	%.c
	$(CC) $(CFLAGS) $(DEFS) -c $<

all:	$(OBJS)

clean:
	$(RM) $(OBJS)
