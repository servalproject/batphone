#include <stdbool.h>
#include <errno.h>
#include <net/if.h>

#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <netlink/msg.h>
#include <netlink/attr.h>

#include "nl80211.h"
#include "iw.h"

static void print_flag(const char *name, int *open)
{
	if (!*open)
		printf(" (");
	else
		printf(", ");
	printf("%s", name);
	*open = 1;
}

static int print_phy_handler(struct nl_msg *msg, void *arg)
{
	struct nlattr *tb_msg[NL80211_ATTR_MAX + 1];
	struct genlmsghdr *gnlh = nlmsg_data(nlmsg_hdr(msg));

	struct nlattr *tb_band[NL80211_BAND_ATTR_MAX + 1];

	struct nlattr *tb_freq[NL80211_FREQUENCY_ATTR_MAX + 1];
	static struct nla_policy freq_policy[NL80211_FREQUENCY_ATTR_MAX + 1] = {
		[NL80211_FREQUENCY_ATTR_FREQ] = { .type = NLA_U32 },
		[NL80211_FREQUENCY_ATTR_DISABLED] = { .type = NLA_FLAG },
		[NL80211_FREQUENCY_ATTR_PASSIVE_SCAN] = { .type = NLA_FLAG },
		[NL80211_FREQUENCY_ATTR_NO_IBSS] = { .type = NLA_FLAG },
		[NL80211_FREQUENCY_ATTR_RADAR] = { .type = NLA_FLAG },
		[NL80211_FREQUENCY_ATTR_MAX_TX_POWER] = { .type = NLA_U32 },
	};

	struct nlattr *tb_rate[NL80211_BITRATE_ATTR_MAX + 1];
	static struct nla_policy rate_policy[NL80211_BITRATE_ATTR_MAX + 1] = {
		[NL80211_BITRATE_ATTR_RATE] = { .type = NLA_U32 },
		[NL80211_BITRATE_ATTR_2GHZ_SHORTPREAMBLE] = { .type = NLA_FLAG },
	};

	struct nlattr *nl_band;
	struct nlattr *nl_freq;
	struct nlattr *nl_rate;
	struct nlattr *nl_mode;
	struct nlattr *nl_cmd;
	struct nlattr *nl_if, *nl_ftype;
	int bandidx = 1;
	int rem_band, rem_freq, rem_rate, rem_mode, rem_cmd, rem_ftype, rem_if;
	int open;

	nla_parse(tb_msg, NL80211_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
		  genlmsg_attrlen(gnlh, 0), NULL);

	if (!tb_msg[NL80211_ATTR_WIPHY_BANDS])
		return NL_SKIP;

	if (tb_msg[NL80211_ATTR_WIPHY_NAME])
		printf("Wiphy %s\n", nla_get_string(tb_msg[NL80211_ATTR_WIPHY_NAME]));

	nla_for_each_nested(nl_band, tb_msg[NL80211_ATTR_WIPHY_BANDS], rem_band) {
		printf("\tBand %d:\n", bandidx);
		bandidx++;

		nla_parse(tb_band, NL80211_BAND_ATTR_MAX, nla_data(nl_band),
			  nla_len(nl_band), NULL);

#ifdef NL80211_BAND_ATTR_HT_CAPA
		if (tb_band[NL80211_BAND_ATTR_HT_CAPA]) {
			__u16 cap = nla_get_u16(tb_band[NL80211_BAND_ATTR_HT_CAPA]);
			print_ht_capability(cap);
		}
		if (tb_band[NL80211_BAND_ATTR_HT_AMPDU_FACTOR]) {
			__u8 exponent = nla_get_u8(tb_band[NL80211_BAND_ATTR_HT_AMPDU_FACTOR]);
			print_ampdu_length(exponent);
		}
		if (tb_band[NL80211_BAND_ATTR_HT_AMPDU_DENSITY]) {
			__u8 spacing = nla_get_u8(tb_band[NL80211_BAND_ATTR_HT_AMPDU_DENSITY]);
			print_ampdu_spacing(spacing);
		}
		if (tb_band[NL80211_BAND_ATTR_HT_MCS_SET] &&
		    nla_len(tb_band[NL80211_BAND_ATTR_HT_MCS_SET]) == 16)
			print_ht_mcs(nla_data(tb_band[NL80211_BAND_ATTR_HT_MCS_SET]));
#endif

		printf("\t\tFrequencies:\n");

		nla_for_each_nested(nl_freq, tb_band[NL80211_BAND_ATTR_FREQS], rem_freq) {
			uint32_t freq;
			nla_parse(tb_freq, NL80211_FREQUENCY_ATTR_MAX, nla_data(nl_freq),
				  nla_len(nl_freq), freq_policy);
			if (!tb_freq[NL80211_FREQUENCY_ATTR_FREQ])
				continue;
			freq = nla_get_u32(tb_freq[NL80211_FREQUENCY_ATTR_FREQ]);
			printf("\t\t\t* %d MHz [%d]", freq, ieee80211_frequency_to_channel(freq));

			if (tb_freq[NL80211_FREQUENCY_ATTR_MAX_TX_POWER] &&
			    !tb_freq[NL80211_FREQUENCY_ATTR_DISABLED])
				printf(" (%.1f dBm)", 0.01 * nla_get_u32(tb_freq[NL80211_FREQUENCY_ATTR_MAX_TX_POWER]));

			open = 0;
			if (tb_freq[NL80211_FREQUENCY_ATTR_DISABLED]) {
				print_flag("disabled", &open);
				goto next;
			}
			if (tb_freq[NL80211_FREQUENCY_ATTR_PASSIVE_SCAN])
				print_flag("passive scanning", &open);
			if (tb_freq[NL80211_FREQUENCY_ATTR_NO_IBSS])
				print_flag("no IBSS", &open);
			if (tb_freq[NL80211_FREQUENCY_ATTR_RADAR])
				print_flag("radar detection", &open);
 next:
			if (open)
				printf(")");
			printf("\n");
		}

		printf("\t\tBitrates (non-HT):\n");

		nla_for_each_nested(nl_rate, tb_band[NL80211_BAND_ATTR_RATES], rem_rate) {
			nla_parse(tb_rate, NL80211_BITRATE_ATTR_MAX, nla_data(nl_rate),
				  nla_len(nl_rate), rate_policy);
			if (!tb_rate[NL80211_BITRATE_ATTR_RATE])
				continue;
			printf("\t\t\t* %2.1f Mbps", 0.1 * nla_get_u32(tb_rate[NL80211_BITRATE_ATTR_RATE]));
			open = 0;
			if (tb_rate[NL80211_BITRATE_ATTR_2GHZ_SHORTPREAMBLE])
				print_flag("short preamble supported", &open);
			if (open)
				printf(")");
			printf("\n");
		}
	}

	if (tb_msg[NL80211_ATTR_MAX_NUM_SCAN_SSIDS])
		printf("\tmax # scan SSIDs: %d\n",
		       nla_get_u8(tb_msg[NL80211_ATTR_MAX_NUM_SCAN_SSIDS]));
	if (tb_msg[NL80211_ATTR_MAX_SCAN_IE_LEN])
		printf("\tmax scan IEs length: %d bytes\n",
		       nla_get_u16(tb_msg[NL80211_ATTR_MAX_SCAN_IE_LEN]));

	if (tb_msg[NL80211_ATTR_WIPHY_FRAG_THRESHOLD]) {
		unsigned int frag;

		frag = nla_get_u32(tb_msg[NL80211_ATTR_WIPHY_FRAG_THRESHOLD]);
		if (frag != (unsigned int)-1)
			printf("\tFragmentation threshold: %d\n", frag);
	}

	if (tb_msg[NL80211_ATTR_WIPHY_RTS_THRESHOLD]) {
		unsigned int rts;

		rts = nla_get_u32(tb_msg[NL80211_ATTR_WIPHY_RTS_THRESHOLD]);
		if (rts != (unsigned int)-1)
			printf("\tRTS threshold: %d\n", rts);
	}

	if (tb_msg[NL80211_ATTR_WIPHY_COVERAGE_CLASS]) {
		unsigned char coverage;

		coverage = nla_get_u8(tb_msg[NL80211_ATTR_WIPHY_COVERAGE_CLASS]);
		/* See handle_distance() for an explanation where the '450' comes from */
		printf("\tCoverage class: %d (up to %dm)\n", coverage, 450 * coverage);
	}

	if (tb_msg[NL80211_ATTR_SUPPORTED_IFTYPES]) {
		printf("\tSupported interface modes:\n");
		nla_for_each_nested(nl_mode, tb_msg[NL80211_ATTR_SUPPORTED_IFTYPES], rem_mode)
			printf("\t\t * %s\n", iftype_name(nl_mode->nla_type));
	}

	if (tb_msg[NL80211_ATTR_SUPPORTED_COMMANDS]) {
		printf("\tSupported commands:\n");
		nla_for_each_nested(nl_cmd, tb_msg[NL80211_ATTR_SUPPORTED_COMMANDS], rem_cmd)
			printf("\t\t * %s\n", command_name(nla_get_u32(nl_cmd)));
	}

	if (tb_msg[NL80211_ATTR_TX_FRAME_TYPES]) {
		printf("\tSupported TX frame types:\n");
		nla_for_each_nested(nl_if, tb_msg[NL80211_ATTR_TX_FRAME_TYPES], rem_if) {
			bool printed = false;
			nla_for_each_nested(nl_ftype, nl_if, rem_ftype) {
				if (!printed)
					printf("\t\t * %s:", iftype_name(nla_type(nl_if)));
				printed = true;
				printf(" 0x%.4x", nla_get_u16(nl_ftype));
			}
			if (printed)
				printf("\n");
		}
	}

	if (tb_msg[NL80211_ATTR_RX_FRAME_TYPES]) {
		printf("\tSupported RX frame types:\n");
		nla_for_each_nested(nl_if, tb_msg[NL80211_ATTR_RX_FRAME_TYPES], rem_if) {
			bool printed = false;
			nla_for_each_nested(nl_ftype, nl_if, rem_ftype) {
				if (!printed)
					printf("\t\t * %s:", iftype_name(nla_type(nl_if)));
				printed = true;
				printf(" 0x%.4x", nla_get_u16(nl_ftype));
			}
			if (printed)
				printf("\n");
		}
	}

	return NL_SKIP;
}

static int handle_info(struct nl80211_state *state,
		       struct nl_cb *cb,
		       struct nl_msg *msg,
		       int argc, char **argv)
{
	nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, print_phy_handler, NULL);

	return 0;
}
__COMMAND(NULL, info, "info", NULL, NL80211_CMD_GET_WIPHY, 0, 0, CIB_PHY, handle_info,
	 "Show capabilities for the specified wireless device.", NULL);
TOPLEVEL(list, NULL, NL80211_CMD_GET_WIPHY, NLM_F_DUMP, CIB_NONE, handle_info,
	 "List all wireless devices and their capabilities.");
TOPLEVEL(phy, NULL, NL80211_CMD_GET_WIPHY, NLM_F_DUMP, CIB_NONE, handle_info, NULL);
