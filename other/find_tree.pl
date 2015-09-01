#    Copyright 2015 Tim Engler, Rareventure LLC
#
#    This file is part of Tiny Travel Tracker.
#
#    Tiny Travel Tracker is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    Tiny Travel Tracker is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.
#

#!/usr/bin/perl

$id = shift @ARGV;
$file = shift @ARGV;

$caring_ids{$id} = 1;
$caring_up_ids{$id} = 1;

while(1)
{
    open(F, $file) || die $file;
    #id=85385,ftdfk=-2147483648,fpdfk=85511,sfk=77237)
    while(<F>)
    {
	if($_ =~ /id=(.*?),ftdfk=(.*?),fpdfk=(.*?),sfk=(.*?)\)/)
	{
	    ($row_id,$ftfk,$fpfk,$sfk) = ($1,$2,$3,$4);
	    
	    if($caring_ids{$row_id})
	    {
		if(!$printed_ids{$row_id})
		{
		    print $_;
		    $printed_ids{$row_id} = 1;
		    $printed_this_round = 1;
		}

		if($sfk > -2147000000)
		{
		    $caring_ids{$sfk} = 1;
		}
		
		if($fpfk > -2147000000)
		{
		    $caring_ids{$fpfk} = 1;
		}
	    }

	    if($caring_up_ids{$sfk})
	    {
		$caring_up_ids{$row_id} = 1;
	    }
	    
	    if($caring_up_ids{$fpfk})
	    {
		$caring_up_ids{$row_id} = 1;

		if(!$printed_ids{$row_id})
		{
		    print "UP: ".$_;
		    $printed_ids{$row_id} = 1;
		    $printed_this_round = 1;
		}
	    }
	}
    }

    last if(!$printed_this_round);
}
