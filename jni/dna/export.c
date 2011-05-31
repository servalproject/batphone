/*
  Export the contents of an binary formatted HLR into plain text.

*/

#include "mphlr.h"

int nyblValue(int c)
{
  if (c>='0'&&c<='9') return c-'0';
  if (c>='A'&&c<='F') return c-'A'+10;
  if (c>='a'&&c<='f') return c-'a'+10;
  exit(setReason("Illegal character `%c' in hexadecimal value.",c));
}

int importHlr(char *textfile)
{
  int j;
  FILE *i;
  unsigned char line[1024];
  char sid[128];
  int state=0;
  int hofs=-1;
  int varinst;
  int varid;
  int varlen;
  unsigned char data[65536];
  int dlen=0;
  int linenum=0;

  if (!strcmp("-",textfile)) i=stdin; 
  else
    if ((i=fopen(textfile,"r"))==NULL) exit(setReason("Could not open import file `%s'"));
  
  line[0]=0; fgets((char *)line,1024,i);
  while(!feof(i))
    {
      int l=strlen((char *)line);
      linenum++;

      /* Strip CR/LFs */
      while(l>0&&(line[l-1]=='\n'||line[l-1]=='\r')) l--; line[l]=0;

      /* Sanity check line */
      for(j=0;j<l;j++) if ((line[j]<' ')||((line[j]>0x7f)&&line[j]!='\n')) {
	  exit(setReason("Illegal character 0x%02x encountered in line %d of HLR import file.",line[j],linenum));
	}
      
      if (line[0]!='#')
	{
	  /* Deal with line */
	  switch(state)
	    {
	    case 0: /* looking for a control line */
	      if (!strncmp("sid=",(char *)line,4)) {
		/* Read SID, and create HLR record for it if one doesn't already exist */
		if (l!=4+SID_SIZE*2)
		  exit(setReason("Malformed sid= line encountered in line %d of HLR import file.",linenum));

		/* Extract SID */
		for(j=0;j<SID_SIZE*2;j++) sid[j]=line[4+j]; sid[SID_SIZE*2]=0;

		/* Find or Create HLR Record */
		if (findHlr(hlr,&hofs,sid,NULL))
		  {
		    /* Have found HLR record for this SID, so no need to create */
		  }
		else
		  {
		    /* No matching HLR record, so create one.
		       Actually, we can't create it until we have the first DID for it,
		       so set hofs to -1 to remind us to do that. */
		    hofs=-1;
		  }

		/* Note that now we are looking for record contents */
		state=1;
	      } else {
		exit(setReason("Unexpected line encountered in line %d of HLR import file -- was looking for a sid= line.",linenum));
	      }
	      break;
	    case 1: /* Reading a HLR record set of values */
	      if (!strcmp("eor",(char *)line))
		{
		  state=0;
		  if (hofs==-1)
		    {
		      /* whoops, we never got around to creating this record because it didn't contain any DIDs.
			 This is something that should probably be complained about.
			 It could also potentially arise when importing an export from an unhappy HLR, but because of the way
			 that we enforce the presense of at least on DID when creating an HLR entry, this should not occur in
			 normal operations. */
		      exit(setReason("Encountered end of exported record at line %d without seeing a DID assignment.  This is bad.\n"
				     "  sid= should be followed by a var=80:00 line.",linenum));
		    }
		}
	      else if (sscanf((char *)line,"var=%02x:%02x len=%d",&varid,&varinst,&varlen)==3) {
		if (varid<0x80||varid>0xff) 
		  exit(setReason("%s:%d var= line contains illegal variable ID.  "
				 "Multi-value variables must be in the range 80-ff (hexadecimal)",textfile,linenum));
		if (varinst<0||varinst>0xff) 
		  exit(setReason("%s:%d var= line contains illegal variable instance number.  Must be in the range 00-ff (hexadecimal)",
				 textfile,linenum));
		if (varlen<1||varlen>65534)
		  exit(setReason("%s:%d var= line contains illegal length.  Must be in the range 1-65534.",textfile,linenum));
		
		/* Okay, we have a valid variable, lets switch to accumulating its value */
		dlen=0;
		state=2;
	      }
	      else if (sscanf((char *)line,"var=%02x len=%d",&varid,&varlen)==2) {
		if (varid>0x7f||varid<0) 
		  exit(setReason("%s:%d var= line contains illegal variable ID.  "
				 "Single-value variables must be in the range 00-7f (hexadecimal)",textfile,linenum));
		varinst=0;
		if (varlen<1||varlen>65534)
		  exit(setReason("%s:%d var= line contains illegal length.  Must be in the range 1-65534.",textfile,linenum));
		
		/* Okay, we have a valid variable, lets switch to accumulating its value */
		dlen=0;
		state=2;
	      } else {
		exit(setReason("%s:%d Syntax error in HLR record.",textfile,linenum));
	      }
	      break;
	    case 2: /* Reading a variable value */
	      /* Read line of data */
	      for(j=0;j<l;)
		{
		  if (dlen>=varlen)
		    exit(setReason("%s:%d Variable value data exceeds stated length.\nThis is what was left after I had taken all I needed: `%s'.",textfile,linenum,&line[j]));
		  switch(line[j])
		    {
		    case '\\':
		      j++;
		      switch(line[j])
			{
			case 'n': data[dlen++]='\n'; break;
			case 'r': data[dlen++]='\r'; break;
			case 'x': data[dlen++]=(hexvalue(line[j+1])<<4)+hexvalue(line[j+2]); j+=2; break;
			default:
			  exit(setReason("%s:%d Illegal \\ sequence `\\%c' encountered in variable value.  Only \\r, \\n and \\x are accepted.",textfile,linenum,line[j]));
			}
		      break;
		    default:
		      data[dlen++]=line[j];
		    }
		  j++;
		}

	      if (dlen==varlen) {
		state=1;
		if (hofs==-1) {
		  if (varid!=VAR_DIDS||varinst!=0)
		    {
		      /* This variable instance is not the first DID, but we still need to create the HLR record.
			 This is naughty.  The first var= line after a sid= line MUST be var=80:00 to specify the first DID. */
		      exit(setReason("%s:%d The first var= line after a sid= line MUST be var=80:00 to specify the first DID.",textfile,linenum));
		    }
		  else
		    {
		      /* Okay, this is the first DID, so now we can create the HLR record.
		         But first, we need to unpack the DID into ascii */
		      char did[SIDDIDFIELD_LEN+1];
		      int zero=0;
		      extractDid(data,&zero,did);
		      printf("DID=%s\n",did);
		      if (createHlr((char *)did,sid))
			exit(setReason("%s:%d createHlr() failed.",textfile,linenum));
		      hofs=0;
		      findHlr(hlr,&hofs,NULL,did);
		      if (hofs<0) 
			exit(setReason("%s:%d Could not find created HLR record.",textfile,linenum));
		    }
		}
		
	      }
	    }
	}

      line[0]=0; fgets((char *)line,1024,i);
    }

  fclose(i);

  return 0;
}

int exportHlr(unsigned char *hlr_file,char *text)
{
  FILE *o;
  int ofs=0,i;
  if (openHlrFile((char *)hlr_file,-1)) exit(setReason("Could not open HLR database"));

  if (!strcmp("-",text)) o=stdout; 
  else
    if ((o=fopen(text,"w"))==NULL) exit(setReason("Could not create export file"));

  while(findHlr(hlr,&ofs,NULL,NULL))
    {
      int hofs=ofs;
      fprintf(o,"# HLR Record at 0x%08x\n",ofs);

      /* Output SID for this record */
      fprintf(o,"sid=");
      for(i=0;i<32;i++) fprintf(o,"%02x",hlr[hofs+4+i]);
      fprintf(o,"\n");

      struct hlrentry_handle *h=openhlrentry(hlr,hofs);
    
      while(h)
	{
	  int cols;
	  if (h->var_id==0&&h->value_len==0) break;

	  fprintf(o,"var=%02x",h->var_id);
	  if (h->var_id&0x80) fprintf(o,":%02x",h->var_instance);
	  fprintf(o," len=%d",h->value_len);
	  for(i=0;vars[i].name;i++) if (vars[i].id==h->var_id) { fprintf(o," name=%s",vars[i].name); break; }
	  fprintf(o,"\n");

	  cols=0;
	  for (i=0;i<h->value_len;i++)
	    {
	      if (h->value[i]>=' '&&h->value[i]<=0x7f&&h->value[i]!='\\'&&(cols||h->value[i]!='#'))
		{
		  fprintf(o,"%c",h->value[i]);
		  cols++;
		}
	      else
		{
		  switch(h->value[i]) {
		  case '\r': fprintf(o,"\\r"); cols+=2; break;
		  case '\n': fprintf(o,"\\n"); cols+=2; break;
		  default:
		    fprintf(o,"\\x%02x",(unsigned char)h->value[i]); cols+=4;
		}
		  if (cols>75) { fprintf(o,"\n"); cols=0; }
		}
	    }
	  if (cols) { fprintf(o,"\n"); cols=0; }

	  h=hlrentrygetent(h);
	}

      /* Mark the end of the record 
	 (as much to make life easier for the import parser as anything) */
      fprintf(o,"eor\n");

      /* Advance to next record and keep searching */
      if (nextHlr(hlr,&ofs)) break;
    }

  return 0;
}
