FROM redis-ipsec:latest

ADD ./ipsec-tools-sentinel3.conf /etc/ipsec-tools.conf

ADD ./psk-sentinel3.txt /etc/racoon/psk.txt


RUN chmod 0400 /etc/racoon/psk.txt

ADD ./secure_sentinel.sh /secure_sentinel.sh

RUN chmod +x /secure_sentinel.sh

RUN mkdir -p /redis

WORKDIR /redis

COPY sentinel.conf .