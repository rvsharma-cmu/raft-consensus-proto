
for i in 1 2 3 4 5
do
	if java RaftTest Initial-Election 8000 | grep -q 'Passed'; then
   		echo "matched Initial-Election"
	else
		echo "No matched Initial-Election"
	fi
	if java RaftTest Re-Election 8000 | grep -q 'Passed'; then
   		echo "matched Re-Election"
	else
                echo "No matched Re-Election"
	fi
	if java RaftTest Basic-Agree 8000 | grep -q 'Passed'; then
   		echo "matched Basic-Agree"
	else
		echo "NO matched Basic-Agree"
	fi
	if java RaftTest Fail-Agree 8000 | grep -q 'Passed'; then
   		echo "matched Fail-Agree"
	else
		echo "NO matched Fail-Agree"
	fi
	if java RaftTest Fail-NoAgree 8000 | grep -q 'Passed'; then
   		echo "matched Fail-NoAgree"
	else
		echo "No matched Fail-NoAgree"
	fi
	if java RaftTest Rejoin 8000 | grep -q 'Passed'; then
   		echo "matched Rejoin"
	else
		echo "No matched Rejoin"
	fi
	if java RaftTest Backup 8000 | grep -q 'Passed'; then
   		echo "matched Backup"
	else
		echo "No matched Backup"
	fi
	if java RaftTest Count 8000 | grep -q 'Passed'; then
   		echo "matched Count"
	else
		echo "No matched Count"
	fi
done

