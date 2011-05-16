#include "mphlr.h"

char *asterisk_extensions_conf="/data/data/org.servalproject/var/extensions_dna.conf";
char *asterisk_reload_command="/data/data/org.servalproject/sbin/asterisk -r extensions reload";
char *asterisk_outbound_sip=NULL;

typedef struct dna_gateway_extension {
  char did[64];
  char requestor_sid[SID_SIZE+1];
  char uri[256-64-SID_SIZE-1-sizeof(int)-(sizeof(time_t)*2)];
  int uriprefixlen;
  time_t created; /* 0 == free */
  time_t expires; /* 0 == free */
} dna_gateway_extension;

#define MAX_CURRENT_EXTENSIONS 1024
dna_gateway_extension extensions[MAX_CURRENT_EXTENSIONS];

int gatewayReadSettings(char *file)
{
  char line[1024];
  FILE *f=fopen(file,"r");
  if (!f) return -1;

  /* Location of extensions.conf file to write
     (really it would be a file you #include from extensions.conf) */
  line[0]=0; fgets(line,1024,f);
  asterisk_extensions_conf=strdup(line);

  /* The command required to get Asterisk to re-read the above file */
  line[0]=0; fgets(line,1024,f);
  asterisk_reload_command=strdup(line);

  /* SIP Registration details for Asterisk outbound gateway
   (my intention is to write them into sip.conf) */
  line[0]=0; fgets(line,1024,f);
  asterisk_outbound_sip=strdup(line);

  /* XXX Need more here I suspect */
  
  fclose(f);
  return 0;
}

int asteriskCreateExtension(char *requestor_sid,char *did,char *uri_out)
{
  /* XXX There are denial of service attacks that we have to consider here.
     Primarily, naughty persons could flood the gateway with false solicitations filling the
     current extension table and slowing asterisk down with lots of reloads.

     Our mitigation strategy is to use a random replacement scheme, except where the same SID
     has an entry in the table already, in which case we should replace that one.
     Replacement entails a search, which should be done using a tree structure for speed.
     Since right now we just want to get the functionality working, we will just do random replacement
     no matter what. 
  */

  /* XXX We should add authentication checks and number validity checks here, e.g., if a gateway only wants to
     allow access for a certain group of users, and/or to only a certain range of numbers */

  /* XXX The "secret" extension is only secret if we encrypt the reply packet! */

  int index=random()%MAX_CURRENT_EXTENSIONS;

  bcopy(requestor_sid,extensions[index].requestor_sid,SID_SIZE);
  strcpy(did,extensions[index].did);
  extensions[index].created=time(0);
  extensions[index].expires=time(0)+3600;
  snprintf(extensions[index].uri,sizeof(extensions[index].uri),"4101*%08x%08x%08x@%s",
	   (unsigned int)random(),(unsigned int)random(),(unsigned int)random(),gatewayuri);
  extensions[index].uriprefixlen=strlen(extensions[index].uri)-strlen(gatewayuri)-1;
  if (extensions[index].uriprefixlen<0) {
    /* Whoops - something wrong with the extension/uri, so kill the record and fail. */
    extensions[index].expires=1;
    return -1;
  }

  if (debug) fprintf(stderr,"Created extension '%s' to dial %s\n",extensions[index].uri,did);
  
  return 0;
}

int asteriskWriteExtensions()
{
  int i;
  time_t now=time(0);
  FILE *out;

  out=fopen(asterisk_extensions_conf,"w");
  if (!out) return -1;

  for(i=0;i<MAX_CURRENT_EXTENSIONS;i++)
    {
      if (extensions[i].expires)
	{
	  if (extensions[i].expires<now)
	    {
	      /* Clear expired gateway extensions */
	      bzero(&extensions[i],sizeof(dna_gateway_extension));
	    }
	  else
	    {
	      extensions[i].uri[extensions[i].uriprefixlen]=0;
	      fprintf(out,
		      "exten => _%s., 1, Dial(SIP/sdnagatewayout/%s)\n"
		      "exten => _%s., 2, Hangup()\n",
		      extensions[i].uri,
		      extensions[i].did,
		      extensions[i].uri);
	      extensions[i].uri[extensions[i].uriprefixlen]='@';
	    }
	}
    }
  fclose(out);
  return 0;
}

int asteriskReloadExtensions()
{
  if (system(asterisk_reload_command))
    return -1;
  else
    return 0;
}

int asteriskGatewayUpP()
{
  /* XXX STUB */
  return 0;
}

int asteriskObtainGateway(char *requestor_sid,char *did,char *uri_out)
{
    /* We use asterisk to provide the gateway service,
       so we need to create a temporary extension in extensions.conf,
       ask asterisk to re-read extensions.conf, and then make sure it has
       a functional SIP gateway.
    */
  
  if (asteriskCreateExtension(requestor_sid,did,uri_out)) return -1;
  if (asteriskWriteExtensions()) return -1;
  if (asteriskReloadExtensions()) return -1;
  if (asteriskGatewayUpP()) return 0; else return -1;

}
