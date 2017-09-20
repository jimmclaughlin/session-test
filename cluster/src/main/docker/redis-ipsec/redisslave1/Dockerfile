FROM redis-ipsec:latest

ADD ./ipsec-tools-redisslave1.conf /etc/ipsec-tools.conf

ADD ./psk-redisslave1.txt /etc/racoon/psk.txt


RUN chmod 0400 /etc/racoon/psk.txt