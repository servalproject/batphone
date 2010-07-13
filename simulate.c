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

double simulatedBER=0;

/*
  We use this function to simulate a lossy link so that we can easily bench-test the
  retransmission protocols.
 */
int dropPacketP(int packet_len)
{
  int i,b;

  long berThreshold=0x7fffffff*simulatedBER;

  if (!simulatedBER) return 0;
  
  for(i=0;i<packet_len;i++)
    for(b=0;b<8;b++)
      if (random()<=berThreshold) return 1;

  return 0;
}
