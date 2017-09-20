all: redis-ipsec redismaster redisslave1 redisslave2 sentinel1 sentinel2 sentinel3

redis-ipsec:
	docker build -t redis-ipsec .

redismaster: redis-ipsec
	cd redismaster && docker build -t redismaster .

redisslave1: redis-ipsec
	cd redisslave1 && docker build -t redisslave1 .

redisslave2: redis-ipsec
	cd redisslave2 && docker build -t redisslave2 .

sentinel1: redis-ipsec
	cd sentinel1 && docker build -t sentinel1 .


sentinel2: redis-ipsec
	cd sentinel2 && docker build -t sentinel2 .

sentinel3: redis-ipsec
	cd sentinel3 && docker build -t sentinel3 .

#session-service: redis-ipsec
#    cd session-service && docker build -t session-service .
