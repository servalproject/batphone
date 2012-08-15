# Common definitions for Serval Batphone test suites.
# Copyright 2012 The Serval Project, Inc.
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

shopt -s extglob

testdefs_sh=$(abspath "${BASH_SOURCE[0]}")
batphone_source_root="${testdefs_sh%/*}"
batphone_build_root="$batphone_source_root"
export TFW_LOGDIR="${TFW_LOGDIR:-$batphone_build_root/testlog}"

atool="$batphone_source_root/aa"

# Some useful regular expressions.  These must work in grep(1) as basic
# expressions, in awk(1) and also in sed(1).
rexp_sid='[0-9a-fA-F]\{64\}'
rexp_did='[0-9+#]\{5,\}'

