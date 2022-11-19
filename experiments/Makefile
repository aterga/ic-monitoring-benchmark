data/mainnet_index_rate.txt:
	echo "Computing index rate graph for production log ..."
	monpoly -sig policy-monitoring/mfotl-policies/predicates.sig \
		-formula index_rate.mfotl \
		-log data/mainnet--3h.unipol.log > $@
