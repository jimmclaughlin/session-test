FROM sessiontest:latest

ADD ./racoon.conf /etc/racoon/racoon.conf

ADD ./ipsec-tools-sentinel3.conf /etc/ipsec-tools.conf

ADD ./psk-sentinel3.txt /etc/racoon/psk.txt


RUN chmod 0400 /etc/racoon/psk.txt