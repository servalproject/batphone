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

int clearResponse(struct response **response)
{
  while(*response)
    {
      struct response *r=*response;
      *response=(*response)->next;
      if (r->response) free(r->response);
      r->response=NULL;
      free(r);
    }
  return 0;
}

int eraseLastResponse(struct response_set *responses)
{
  if (!responses) return -1;
  if (responses->last_response)
    {
      struct response *newtail;
      if (responses->last_response->prev) responses->last_response->prev->next=NULL;
      newtail=responses->last_response->prev;
      if (responses->responses==responses->last_response) responses->responses=NULL;
      clearResponse(&responses->last_response);
      responses->last_response=newtail;
      responses->response_count--;
    }
  return 0;
}

int responseFromPeer(struct response_set *responses,int peerId)
{
  int byte;
  int bit;

  if (peerId<0||peerId>peer_count) return -1;
  if (!responses) return -1;
  if (!responses->reply_bitmask)
    {
      responses->reply_bitmask=calloc(1,(peer_count>>3)+(peer_count&7)?1:0);
      if (!responses->reply_bitmask) return -1;
    }

  byte=peerId>>3;
  bit=peerId&7;

  responses->reply_bitmask[byte]|=1<<bit;

  return 0;

}

int responseFromPeerP(struct response_set *responses,int peerId)
{
  int byte;
  int bit;

 if (!responses) return 0;
 if (!responses->reply_bitmask) return 0;

 if (peerId<0||peerId>peer_count) return 0;

  byte=peerId>>3;
  bit=peerId&7;

  return responses->reply_bitmask[byte]&(1<<bit);
}

int clearResponses(struct response_set *responses)
{
  struct response *r;

  if (!responses) return -1;

  r=responses->responses;
  while(r)
    {
      struct response *rr=r;
      r=r->next;
      free(rr);
    }

  if (responses->reply_bitmask) free(responses->reply_bitmask);
  responses->reply_bitmask=NULL;
  
  responses->last_response=NULL;
  responses->responses=NULL;
  responses->response_count=0;
  return 0;
}
