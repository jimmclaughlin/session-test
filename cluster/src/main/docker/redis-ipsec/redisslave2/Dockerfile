FROM redis-ipsec:latest

ADD ./ipsec-tools-redisslave2.conf /etc/ipsec-tools.conf

ADD ./psk-redisslave2.txt /etc/racoon/psk.txt


RUN chmod 0400 /etc/racoon/psk.txt