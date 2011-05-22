#include "mphlr.h"

char *asterisk_extensions_conf="/data/data/org.servalproject/var/extensions_dna.conf";
char *asterisk_binary="/data/data/org.servalproject/sbin/asterisk";
char *temp_file="/data/data/org.servalproject/var/temp.out";

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
  asterisk_binary=strdup(line);

  /* Temporary file for catching Asterisk output from -rx commands */
  line[0]=0; fgets(line,1024,f);
  temp_file=strdup(line);


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
    if (debug) fprintf(stderr,"%s:%d: Generated extension appears to be malformed.\n",__FUNCTION__,__LINE__);
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
  if (!out) {
    if (debug) fprintf(stderr,"%s:%d: Could not write extensions file '%s'.\n",__FUNCTION__,__LINE__,asterisk_extensions_conf);
    return -1;
  }

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
  char cmd[8192];
  snprintf(cmd,8192,"%s -rx \"dialplan reload\"",asterisk_binary);
  if (system(cmd))
    {
      if (debug) fprintf(stderr,"%s:%d: Dialplan reload failed.\n",__FUNCTION__,__LINE__);
      return -1;
    }
  else
    return 0;
}

int asteriskGatewayUpP()
{
  int registered=0;

  /* 
     1. Run "serval dna gateway" command to enquire of gateway status?
        No, as that enquires of the wrong DNA instance.  Also, we are now controlling the
	enable/disable by checking the outbound SIP gateway status in asterisk, and the
	BatPhone settings screen controls the availability of that by re-writing asterisk config files.
     2. Check that outbound SIP gateway is up: 
        asterisk -r "sip show registry"
	and grep output for active links.
	XXX - Annoyingly we need to know the server hostname to use the output of this list in
	a fool-proof manner.  However, if we work on the assumption of only one SIP registration existing, 
	being ours, then we can ignore the hostname.
   */
  char cmd[8192];
  snprintf(cmd,8192,"%s -rx \"sip show registry\" > %s",asterisk_binary,temp_file);
  system(cmd);
  FILE *f=fopen(temp_file,"r");
  if (!f) {
    if (debug) fprintf(stderr,"%s:%d: Could not read result from \"sip show registry\".\n",__FUNCTION__,__LINE__);	
    return 0;
  }
  
  /* Output of command is something like:
     Host                            Username       Refresh State                Reg.Time                 
     houstin.voip.ms:5060            103585             120 Unregistered                                  
  */
  
  cmd[0]=0; fgets(cmd,1024,f);
  while(cmd[0]) {
    char host[1024];
    int port;
    char user[1024];
    int refresh;
    char state[1024];

    if (sscanf(cmd,"%[^:]:%d%*[ ]%[^ ]%*[ ]%d%*[ ]%[^ ]",
	       host,&port,user,&refresh,state)==5)
      {
	// state == "Unregistered" if unavailable, although other values are possible.
	// state == "Registered" if available.
	if (!strcasecmp(state,"Registered")) registered=1; else registered=0;
      }
    cmd[0]=0; fgets(cmd,1024,f);
  }
  
  fclose(f);

  return registered;
}

int asteriskObtainGateway(char *requestor_sid,char *did,char *uri_out)
{
    /* We use asterisk to provide the gateway service,
       so we need to create a temporary extension in extensions.conf,
       ask asterisk to re-read extensions.conf, and then make sure it has
       a functional SIP gateway.
    */
  
  if (!asteriskGatewayUpP()) 
    { if (debug) fprintf(stderr,"Asterisk gatway is not up, so not offering gateway.\n"); return -1; }
  if (asteriskCreateExtension(requestor_sid,did,uri_out)) 
    {
      if (debug) fprintf(stderr,"asteriskCreateExtension() failed, so not offering gateway.\n");
      return -1;
    }
  if (asteriskWriteExtensions())
    {
      if (debug) fprintf(stderr,"asteriskWriteExtensions() failed, so not offering gateway.\n");
      return -1;
    }
  if (asteriskReloadExtensions()) 
    {
      if (debug) fprintf(stderr,"asteriskReloadExtensions() failed, so not offering gateway.\n");
      return -1;
    }
  return 0;
}
