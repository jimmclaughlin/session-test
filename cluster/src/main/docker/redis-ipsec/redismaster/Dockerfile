FROM redis-ipsec:latest

ADD ./ipsec-tools-redismaster.conf /etc/ipsec-tools.conf

ADD ./psk-redismaster.txt /etc/racoon/psk.txt

RUN chmod 0400 /etc/racoon/psk.txt
