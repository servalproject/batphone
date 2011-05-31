/* 
Serval Distributed Numbering Architecture (DNA)
Copyright (C) 2010 Paul Gardner-Stephen 

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#include "mphlr.h"

int bcompare(unsigned char *a,unsigned char *b,size_t len)
{
  int i;
  for(i=0;i<len;i++) if (a[i]<b[i]) return -1; else if (a[i]>b[i]) return 1;
  return 0;
}

int nextHlr(unsigned char *hlr,int *ofs)
{
  int record_length;

  if (!ofs) return setReason("nextHlr passed NULL pointer.");
  if (*ofs>=hlr_size) return -1;

  /* Get length of this record */
  record_length =hlr[(*ofs)+3]<<0;
  record_length|=hlr[(*ofs)+2]<<8;
  record_length|=hlr[(*ofs)+1]<<16;
  record_length|=hlr[(*ofs)+0]<<24;

  if (!record_length) return -1;

  (*ofs)+=record_length;
  return 0;
}

char sid_string[SID_SIZE*2+1];
char *hlrSid(unsigned char *hlr,int ofs)
{
  int o=ofs+4;
  extractSid(hlr,&o,sid_string);
  return sid_string;
}

int findHlr(unsigned char *hlr,int *ofs,char *sid,char *did)
{
  unsigned int record_length;
  int match=0;
  int records_searched=0;
  int pid_len=0;
  unsigned char packed_id[40];

  if ((*ofs)>=hlr_size) return 0;

  if (debug>1) fprintf(stderr,"Searching for HLR record sid=[%s]/did=[%s]\n",sid?sid:"NULL",did?did:"NULL");
  
  if (did&&did[0]) {
    /* Make packed version of DID so that we can compare faster with the DIDs in the HLR */
    if (stowDid(packed_id,&pid_len,did)) return setReason("DID appears to be invalid");
    /* Find significant length of packed DID */
    for(pid_len=0;pid_len<DID_MAXSIZE;pid_len++) if ((packed_id[pid_len]&0x0f)==0x0f) { pid_len++; break; }
    if (debug>1) dump("Searching for DID records that match",packed_id,pid_len);
  }

  if (sid&&sid[0]) {
    /* Make packed version of SID for fast comparison */
    if (stowSid(packed_id,pid_len,sid)) return setReason("SID appears to be invalid");
    pid_len=SID_SIZE;
  }

  while(!match)
    {
      /* Get length of this record */
      record_length =hlr[(*ofs)+3]<<0;
      record_length|=hlr[(*ofs)+2]<<8;
      record_length|=hlr[(*ofs)+1]<<16;
      record_length|=hlr[(*ofs)+0]<<24;
      
      if (!record_length) return 0;

      if (debug>1) fprintf(stderr,"Considering HLR entry @ 0x%x\n",*ofs);
  
      records_searched++;
  
      if (sid&&sid[0]) {
	/* Lookup by SID, so just see if it matches */
	if (!bcompare(packed_id,&hlr[(*ofs)+4],SID_SIZE)) {
	  if (debug>1) fprintf(stderr,"Found requested SID at address 0x%x.\n",*ofs);
	  match=1;
	}
      }
      if (did&&did[0]) {
	/* Lookup by did, so see if there are any matching DID entries for this subscriber */
	int rofs=(*ofs);
	struct hlrentry_handle *h=openhlrentry(hlr,rofs);
	while(h)
	  {
	    /* Search through variables for matching DIDs */
	    if (debug>2) {
	      fprintf(stderr,"Considering variable 0x%02x, instance %d.\n",
		      h->var_id,h->var_instance);
	      dump("variable value",h->value,h->value_len);
	    }
	    if (h->var_id==VAR_DIDS) { /* DID entry  */
	      if (debug>2) fprintf(stderr,"Checking DID against record DID\n");
	      if (!bcompare(packed_id,h->value,pid_len)) {		
		if (debug>1) fprintf(stderr,"Found matching DID in HLR record #%d\n",records_searched);
		match=1;
		break;
	      }
	    }
	    else
	      {
		if (debug>2) fprintf(stderr,"Skipping non-DID variable while searching for DID.\n");
	      }		
	    h=hlrentrygetent(h);
	  }
      }
  
      /* For each match ... */
      if (match) 
	{
	  if (debug>1) fprintf(stderr,"Returning HLR entry @ 0x%x\n",*ofs);
	  return 1;
	}
  
      /* Consider next record */
      (*ofs)+=record_length;

      if ((*ofs)>=hlr_size) return 0;
    }

  return 0;
}

int createHlr(char *did,char *sid) {
  int i;
  int record_offset=0;

  /* Generate random SID */
  for(i=0;i<64;i++) sid[i]=hexdigit[random()&0xf]; sid[64]=0;
  if (debug>1) fprintf(stderr,"Creating new HLR entry with sid %s\n",sid);
  
  /* Find first free byte of HLR */
  findHlr(hlr,&record_offset,NULL,NULL);

  if (record_offset>=hlr_size)
    {
      /* No space */
      return setReason("No space in HLR for a new record");
    }
  else
    {
      /* We have found space, but is it enough? */
      int bytes=hlr_size-record_offset;
      if (bytes<1024) return setReason("<1KB space in HLR");
      
      /* Write shiny fresh new record.
	 32bit - record length 
	 32 bytes - SID
	 Total length = 4+32=36 bytes.
      */
      if (stowSid(hlr,record_offset+4,sid)) return setReason("Could not store SID in new HLR entry");
	
      /* Write length last of all to make entry valid */
      hlr[record_offset]=0;
      hlr[record_offset+1]=0;
      hlr[record_offset+2]=0;
      hlr[record_offset+3]=36;

      /* Store the DID */
      {
	unsigned char packeddid[DID_MAXSIZE];
	int pdidlen=0;
	stowDid(packeddid,&pdidlen,did);
	/* Work out reduced length of DID */
	for(pdidlen=1;pdidlen<DID_MAXSIZE;pdidlen++) if (packeddid[pdidlen-1]==0xff) break;
	hlrSetVariable(hlr,record_offset,VAR_DIDS,0x00,packeddid,pdidlen);
      }

      if (debug) fprintf(stderr,"Created new 36 byte HLR record for DID=[%s] @ 0x%x with SID=[%s]\n",
			 did,record_offset,sid);
      if (debug>2) dump("after HLR create",&hlr[0],256);
      return 0;
    }

  return setReason("Unreachable code turned out not to be");
}

struct hlrentry_handle hlr_handle;

int hlrGetRecordLength(unsigned char *hlr,int hofs)
{
  int record_length;
  record_length =hlr[hofs+3]<<0;
  record_length|=hlr[hofs+2]<<8;
  record_length|=hlr[hofs+1]<<16;
  record_length|=hlr[hofs+0]<<24;

  if (debug>2) fprintf(stderr,"HLR record @ 0x%x is %d bytes long.\n",hofs,record_length);

  return record_length;
}

int hlrSetRecordLength(unsigned char *hlr,int hofs,int length)
{
  hlr[hofs+3]=length&0xff;
  hlr[hofs+2]=(length>>8)&0xff;
  hlr[hofs+1]=(length>>16)&0xff;
  hlr[hofs+0]=(length>>24)&0xff;
  return 0;
}

/*
  XXX We could return a fancy struct, and maybe we should.
  But returning a long long and using the two 32bit halves is easy for now, and has a
  certain efficiency to it. */
struct hlrentry_handle *openhlrentry(unsigned char *hlr,int hofs)
{
  int record_length=hlrGetRecordLength(hlr,hofs);

  /* If record has zero length, then open fails */
  if (!record_length)
    {
      if (debug>2) fprintf(stderr,"HLR record is zero length -- aborting.\n");
      return NULL;
    }

  bzero(&hlr_handle,sizeof(hlr_handle));

  hlr_handle.record_length=record_length;
  hlr_handle.hlr=hlr;
  hlr_handle.hlr_offset=hofs;
  hlr_handle.var_id=-1;
  hlr_handle.var_instance=-1;
  hlr_handle.value=NULL;
  hlr_handle.value_len=-1;
  hlr_handle.entry_offset=0;

  /* Return offset of start of HLR entry and the offset of the first variable */
  return hlrentrygetent(&hlr_handle);
}

struct hlrentry_handle *hlrentrygetent(struct hlrentry_handle *h)
{
  int ptr;
  
  if (!h) return NULL;

  if (h->entry_offset==0)
    {
      /* First entry */
      if (debug>2) fprintf(stderr,"Considering first entry of HLR record.\n");
      h->entry_offset=HLR_RECORD_LEN_SIZE+SID_SIZE;
    }
  else
    {
      /* subsequent entry */
      if (debug>2) fprintf(stderr,"Considering entry @ 0x%x\n",h->entry_offset);
      h->entry_offset+=1+2+h->value_len+(h->var_id&0x80?1:0);
    }

  /* XXX Check if end of record */
  if (h->entry_offset>=h->record_length) {
    if (debug>2) fprintf(stderr,"Reached end of HLR record (%d>=%d).\n",h->entry_offset,h->record_length);
    return NULL;
  }

  /* XXX Extract variable */
  ptr=h->hlr_offset+h->entry_offset;
  if (debug>2) fprintf(stderr,"Extracting HLR variable @ 0x%x\n",ptr);
  h->var_id=hlr[ptr];
  h->value_len=(hlr[ptr+1]<<8)+hlr[ptr+2];
  ptr+=3;
  if (h->var_id&0x80) h->var_instance=hlr[ptr++];
  h->value=&h->hlr[ptr];

  return h;
}

int hlrGetVariable(unsigned char *hlr,int hofs,int varid,int varinstance,
		   unsigned char *value,int *len)
{  
  struct hlrentry_handle *h;
  int hlr_offset=-1;

  h=openhlrentry(hlr,hofs);

  /* Find the place in the HLR record where this variable is */
  while(h)
    {     
      if ((h->var_id<varid)
	  ||(h->var_id==varid&&h->var_instance<varinstance))
	hlr_offset=h->entry_offset;
      else
	{
	  /* Value is here if anywhere */
	  if (h->var_id>varid||h->var_instance>varinstance)
	    return setReason("No such variable instance");
	  if (h->value_len>*len) return setReason("Value too long for buffer");
	  bcopy(h->value,value,h->value_len);
	  *len=h->value_len;
	  return 0;
	  break;
	}
      h=hlrentrygetent(h);
    }

  return setReason("No such variable instance");
}

int hlrSetVariable(unsigned char *hlr,int hofs,int varid,int varinstance,
		   unsigned char *value,int len)
{
  /* hlr & hofs identify the start of a HLR entry. */

  struct hlrentry_handle *h;
  int hlr_offset=-1;
  int hlr_size=hlrGetRecordLength(hlr,hofs);

  if (debug) fprintf(stderr,"hlrSetVariable(varid=%02x, instance=%02x, len=%d)\n",
		     varid,varinstance,len);

  h=openhlrentry(hlr,hofs);

  /* Find the place in the HLR record where this variable should go */
  while(h)
    {     
      if (debug>1) fprintf(stderr,"h->var_id=%02x, h->h->var_instance=%02x, h->entry_offset=%x\n",
			   h->var_id,h->var_instance,h->entry_offset);
      if ((h->var_id<varid)
	  ||(h->var_id==varid&&h->var_instance<varinstance))
	{
	  hlr_offset=h->entry_offset;
	  if (debug>1) fprintf(stderr,"Found variable instance prior: hlr_offset=%d.\n",hlr_offset);
	}
      else
	{
	  /* Value goes here */
	  if (debug>1) fprintf(stderr,"Found variable instance to overwrite: hlr_offset=%d.\n",hlr_offset);
	  hlr_offset=h->entry_offset;
	  break;
	}
      h=hlrentrygetent(h);
    }

  /* XXX Race condition:  If power is lost half way through here, then it is possible for
     the record to be left in an inconsistent state */

  if (h&&hlr_offset>-1)
    {
      if (debug>2) printf("hlr_offset=%d\n",hlr_offset);
      if (h&&h->var_id==varid&&h->var_instance==varinstance)
	{
		int existing_size;
	  /* Replace existing value */
	  if (debug) fprintf(stderr,"Replacing value in HLR\n");
	  existing_size=1+2+(h->var_id&0x80?1:0)+h->value_len;
	  hlrMakeSpace(hlr,hofs,hlr_offset,1+2+len+(varid&0x80?1:0)-existing_size);
	}
      else
	{
	  /* Insert value here */
	  if (debug) fprintf(stderr,"Inserting value in HLR\n");
	  hlrMakeSpace(hlr,hofs,hlr_offset,1+2+len+(varid&0x80?1:0));
	}
    }
  else
    {
      /* HLR record has no entries, or this entry needs to go at the end,
	 so insert value at end of the record */
      if (debug) fprintf(stderr,"Inserting value at end of HLR @ 0x%x\n",hlr_size);
      hlrMakeSpace(hlr,hofs,hlr_size,1+2+len+(varid&0x80?1:0));
      hlr_offset=hlr_size;
    }

  return hlrStowValue(hlr,hofs,hlr_offset,varid,varinstance,value,len);
}

int hlrStowValue(unsigned char *hlr,int hofs,int hlr_offset,
		 int varid,int varinstance,unsigned char *value,int len)
{
  int ptr=hofs+hlr_offset;

  hlr[ptr++]=varid;
  hlr[ptr++]=(len>>8)&0xff;
  hlr[ptr++]=len&0xff;
  if (varid&0x80) hlr[ptr++]=varinstance;
  bcopy(value,&hlr[ptr],len);
  ptr+=len;
    
  return 0;
}

int hlrMakeSpace(unsigned char *hlr,int hofs,int hlr_offset,int bytes)
{
  int length;
  /* Deal with easy case first */
  if (!bytes) return 0;

  /* Shift rest of HLR up/down.
     If down, back-fill bytes with zeros. */
  bcopy(&hlr[hofs+hlr_offset],&hlr[hofs+hlr_offset+bytes],
	hlr_size-(hofs+hlr_offset+bytes));
  if (bytes<0) bzero(&hlr[hlr_size-bytes],0-bytes);

  /* Update record length */
  length=hlrGetRecordLength(hlr,hofs);
  length+=bytes;
  hlrSetRecordLength(hlr,hofs,length);
  if (debug>1) fprintf(stderr,"hlrMakeSpace: HLR entry now %d bytes long.\n",length);

  return 0;
}

int hlrDump(unsigned char *hlr,int hofs)
{
  struct hlrentry_handle *h=openhlrentry(hlr,hofs);
    
  fprintf(stderr,"Dumping HLR entry @ 0x%x\n",hofs);
  while(h)
    {
      fprintf(stderr,"   var=%02x",h->var_id);
      if (h->var_id&0x80) fprintf(stderr,"/%02x",h->var_instance);
      fprintf(stderr," len=%d\n",h->value_len);
      h=hlrentrygetent(h);
    }
	
  return 0;
}

int openHlrFile(char *backing_file,int size)
{
  /* Get backing store */
  if (!backing_file)
    {
      if (size<0) exit(setReason("You must provide an HLR file or size"));

      /* transitory storage of HLR data, so just malloc() the memory */
      hlr=calloc(size,1);
      if (!hlr) exit(setReason("Failed to calloc() HLR database."));
      if (debug) fprintf(stderr,"Allocated %d byte temporary HLR store\n",size);
    }
  else
    {
      unsigned char zero[8192];
      FILE *f=fopen(backing_file,"r+");
      if (!f) f=fopen(backing_file,"w+");
      if (!f) exit(setReason("Could not open backing file."));
      bzero(&zero[0],8192);
      fseek(f,0,SEEK_END);
      errno=0;

      /* Obtain size from existing backing file */
      if (size<0) size=ftell(f);

      while(ftell(f)<size)
        {
          int r;
          fseek(f,0,SEEK_END);
          if ((r=fwrite(zero,8192,1,f))!=1)
            {
              perror("fwrite");
              exit(setReason("Could not enlarge backing file to requested size (short write)"));
            }
          fseek(f,0,SEEK_END);
        }
      
      if (errno) perror("fseek");
      if (fwrite("",1,1,f)!=1)
        {
          fprintf(stderr,"Failed to set backing file size.\n");
          perror("fwrite");
        }
      hlr=(unsigned char *)mmap(NULL,size,PROT_READ|PROT_WRITE,MAP_SHARED|MAP_NORESERVE,fileno(f),0);
      if (hlr==MAP_FAILED) {
        perror("mmap");
        exit(setReason("Memory mapping of HLR backing file failed."));
      }
      if (debug) fprintf(stderr,"Allocated %d byte HLR store backed by file `%s'\n",
                         size,backing_file);
    }
  hlr_size=size;
  
  return 0;
}
