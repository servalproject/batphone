/*
  Serval Overlay Mesh Network.

  Basically we use UDP broadcast to send link-local, and then implement a BATMAN-like protocol over the top of that.
  
  Each overlay packet can contain one or more encapsulated packets each addressed using Serval DNA SIDs, with source, 
  destination and next-hop addresses.

  The use of long (relative to IPv4) ECC160 addresses means that it is a really good idea to have neighbouring nodes
  exchange lists of peer aliases so that addresses can be summarised, possibly using less space than IPv4 would have.

  Byzantine Robustness is a goal, so we have to think about all sorts of malicious failure modes.

  One approach to help byzantine robustness is to have multiple signature shells for each hop for mesh topology packets.
  Thus forging a report of closeness requires forging a signature.  As such frames are forwarded, the outermost signature
  shell is removed.

  
 */

#include "mphlr.h"

int overlay_socket=-1;

typedef struct overlay_buffer {
  unsigned char *bytes;
  int length;
  int allocSize;
  int checkpointLength;
  int sizeLimit;
} overlay_buffer;

int ob_unlimitsize(overlay_buffer *b);

typedef struct overlay_payload {
  struct overlay_payload *prev;
  struct overlay_payload *next;

  /* We allows 256 bit addresses and 32bit port numbers */
  char src[SIDDIDFIELD_LEN];
  char dst[SIDDIDFIELD_LEN];
  int srcPort;
  int dstPort;
  
  /* Hops before packet is dropped */
  unsigned char ttl;
  unsigned char trafficClass;

  unsigned char srcAddrType;
  unsigned char dstAddrType;

  /* Method of encryption if any employed */
  unsigned char cipher;

  /* Payload flags */
  unsigned char flags;

  /* Pointer to the payload itself */
  unsigned char *payload;
  int payloadLength;
} overlay_payload;

typedef struct overlay_txqueue {
  overlay_payload *first;
  overlay_payload *last;
  int length;
  int maxLength;
  /* Latency target in ms for this traffic class */
  int latencyTarget;
} overlay_txqueue;

/* XXX Need to initialise these:
   Real-time queue for voice
   Real-time queue for video (lower priority than voice)
   Ordinary service queue
   Rhizome opportunistic queue

   (Mesh management doesn't need a queue, as each overlay packet is tagged with some mesh management information)
 */
overlay_txqueue overlay_tx[4];

int overlay_sock=-1;

int overlay_init()
{
  struct sockaddr_in bind_addr;
  
  overlay_sock=socket(PF_INET,SOCK_DGRAM,0);
  if (overlay_sock<0) {
    fprintf(stderr,"Could not create overlay UDP socket.\n");
    perror("socket");
    exit(-3);
  }

  bind_addr.sin_family = AF_INET;
  bind_addr.sin_port = htons( PORT_DNA );
  bind_addr.sin_addr.s_addr = htonl( INADDR_ANY );
  if(bind(sock,(struct sockaddr *)&bind_addr,sizeof(bind_addr))) {
    fprintf(stderr,"MP HLR server could not bind to UDP port %d\n", PORT_DNA);
    perror("bind");
    exit(-3);
  }

  return 0;
}

int overlay_rx_messages()
{
  if (overlay_socket==-1) overlay_init();

  return 0;
}

int overlay_tx_messages()
{
  if (overlay_socket==-1) overlay_init();

  return 0;
}

int overlay_broadcast_ensemble(char *bytes,int len)
{
  struct sockaddr_in s;

  memset(&s, '\0', sizeof(struct sockaddr_in));
  s.sin_family = AF_INET;
  s.sin_port = htons( PORT_OVERLAY );
  s.sin_addr.s_addr = htonl( INADDR_BROADCAST );

  if(sendto(overlay_socket, bytes, len, 0, (struct sockaddr *)&s, sizeof(struct sockaddr_in)) < 0)
    /* Failed to send */
    return -1;
  else
    /* Sent okay */
    return 0;
}

int overlay_payload_verify()
{
  /* Make sure that an incoming payload has a valid signature from the sender.
     This is used to prevent spoofing */

  return -1;
}


overlay_buffer *ob_new(int size)
{
  overlay_buffer *ret=calloc(sizeof(overlay_buffer),1);
  if (!ret) return NULL;

  ob_unlimitsize(ret);

  return ret;
}

int ob_free(overlay_buffer *b)
{
  if (!b) return -1;
  if (b->bytes) free(b->bytes);
  b->bytes=NULL;
  b->allocSize=0;
  b->sizeLimit=0;
  free(b);
  return 0;
}

int ob_checkpoint(overlay_buffer *b)
{
  if (!b) return -1;
  b->checkpointLength=b->length;
  return 0;
}

int ob_rewind(overlay_buffer *b)
{
  if (!b) return -1;
  b->length=b->checkpointLength;
  return 0;
}

int ob_limitsize(overlay_buffer *b,int bytes)
{
  if (!b) return -1;
  if (b->length>bytes) return -1;
  if (b->checkpointLength>bytes) return -1;
  if (bytes<0) return -1;
  b->sizeLimit=bytes;
  return 0;
}

int ob_unlimitsize(overlay_buffer *b)
{
  if (!b) return -1;
  b->sizeLimit=-1;
  return 0;
}

int ob_makespace(overlay_buffer *b,int bytes)
{
  if (b->sizeLimit!=-1) {
    if (b->length+bytes>b->sizeLimit) return -1;
  }
  if (b->length+bytes<b->allocSize)
    {
      int newSize=b->length+bytes;
      if (newSize<64) newSize=64;
      if (newSize&63) newSize+=64-(newSize&63);
      if (newSize>1024) {
	if (newSize&1023) newSize+=1024-(newSize&1023);
      }
      if (newSize>65536) {
	if (newSize&65535) newSize+=65536-(newSize&65535);
      }
      unsigned char *r=realloc(b->bytes,newSize);
      if (!r) return -1;
      b->bytes=r;
      b->allocSize=newSize;
      return 0;
    }
  else
    return 0;
}

int ob_append_bytes(overlay_buffer *b,unsigned char *bytes,int count)
{
  if (ob_makespace(b,count)) return -1;
  
  bcopy(bytes,&b->bytes[b->length],count);
  b->length+=count;
  return 0;
}

int ob_append_short(overlay_buffer *b,unsigned short v)
{
  unsigned short s=htons(v);
  return ob_append_bytes(b,(unsigned char *)&s,sizeof(unsigned short));
}

int ob_append_int(overlay_buffer *b,unsigned int v)
{
  unsigned int s=htonl(v);
  return ob_append_bytes(b,(unsigned char *)&s,sizeof(unsigned int));
}

int overlay_get_nexthop(overlay_payload *p,unsigned char *hopout,int *hopaddrlen)
{
  return -1;
}

int overlay_payload_package_fmt1(overlay_payload *p,overlay_buffer *b)
{
  /* Convert a payload structure into a series of bytes.
     Also select next-hop address to help payload get to its' destination */

  unsigned char nexthop[SIDDIDFIELD_LEN+1];
  int nexthoplen=0;

  overlay_buffer *headers=ob_new(256);

  if (!headers) return -1;
  if (!p) return -1;
  if (!b) return -1;

  /* Build header */
  int fail=0;

  if (overlay_get_nexthop(p,nexthop,&nexthoplen)) fail++;
  if (ob_append_bytes(headers,nexthop,nexthoplen)) fail++;

  /* XXX Can use shorter fields for different address types */
  if (ob_append_bytes(headers,(unsigned char *)p->src,SIDDIDFIELD_LEN)) fail++;
  if (ob_append_bytes(headers,(unsigned char *)p->dst,SIDDIDFIELD_LEN)) fail++;
  
  if (fail) {
    ob_free(headers);
    return -1;
  }

  /* Write payload format plus total length of header bits */
  if (ob_makespace(b,2+headers->length+p->payloadLength)) {
    /* Not enough space free in output buffer */
    ob_free(headers);
    return -1;
  }

  /* Package up headers and payload */
  ob_checkpoint(b);
  if (ob_append_short(b,0x1000|(p->payloadLength+headers->length))) fail++;
  if (ob_append_bytes(b,headers->bytes,headers->length)) fail++;
  if (ob_append_bytes(b,p->payload,p->payloadLength)) fail++;

  /* XXX SIGNATURE! */

  ob_free(headers);

  if (fail) { ob_rewind(b); return -1; } else return 0;
}

overlay_payload *overlay_payload_unpackage(overlay_buffer *b) {
  /* Extract the payload at the current location in the buffer. */

  return NULL;
}
