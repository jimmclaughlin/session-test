from gremlins import faults, tc


def introduce_fifty_percent_packet_loss():
    def do():
        tc.introduce_packet_loss(50)

    return do

fifty_percent_packet_loss = introduce_fifty_percent_packet_loss()

clear_network_faults = faults.clear_network_faults()
introduce_packet_loss = faults.introduce_network_packet_loss()
introduce_partition = faults.introduce_network_partition()
introduce_latency = faults.introduce_network_latency()
introduce_packet_reordering = faults.introduce_packet_reordering()

