
for i in 1 2 3 4 5
do
	if java RaftTest Initial-Election 8000 | grep -q 'Passed'; then
   		echo "matched Initial-Election"
	fi
	else
		echo "No matched Initial-Election"
	if java RaftTest Re-Election 8000 | grep -q 'Passed'; then
   		echo "matched Re-Election"
	fi
	else
                echo "No matched Re-Election"
	if java RaftTest Basic-Agree 8000 | grep -q 'Passed'; then
   		echo "matched Basic-Agree"
	fi
	else
		echo "NO matched Basic-Agree"
	if java RaftTest Fail-Agree 8000 | grep -q 'Passed'; then
   		echo "matched Fail-Agree"
	fi
	else
		echo "NO matched Fail-Agree"
	if java RaftTest Fail-NoAgree 8000 | grep -q 'Passed'; then
   		echo "matched Fail-NoAgree"
	fi
	else
		echo "No matched Fail-NoAgree"
	if java RaftTest Rejoin 8000 | grep -q 'Passed'; then
   		echo "matched Rejoin"
	fi
	else
		echo "No matched Rejoin"
	if java RaftTest Backup 8000 | grep -q 'Passed'; then
   		echo "matched Backup"
	fi
	else
		echo "No matched Backup"
	if java RaftTest Count 8000 | grep -q 'Passed'; then
   		echo "matched Count"
	fi
	else
		echo "No matched Count"
done

