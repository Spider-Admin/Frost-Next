/*
  BoardUpdateInformation.java / Frost
  Copyright (C) 2008  Frost Project <jtcfrost.sourceforge.net>

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License as
  published by the Free Software Foundation; either version 2 of
  the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package frost.boards;

public class BoardUpdateInformation {

    // -> show non-modal dialog, change displayed board when board tree selection changes
    //      ComboBox for Boardname (extra, Text = "boardname (updcount)")
    //      ComboBox for dateString

    // - maybe compute : indices per X minutes

    private final Board board;
    private final String dateString;
    private final long dateMillis;

//    private long updateInformationLastUpdated = -1;

    private int countTriedIndices = 0;
    private int currentIndex = -1;
    private int maxIndex = -1;
    private int maxSuccessfulIndex = -1;

    private int countADNF = 0;    // ALL_DATA_NOT_FOUND
    private int countDNF = 0;     // DATA_NOT_FOUND
    private int countInvalid = 0; // invalid msgs
    private int countValid = 0;   // valid messages

    public BoardUpdateInformation(final Board newBoard, final String newDateString, final long newDateMillis) {
        board = newBoard;
        dateString = newDateString;
        dateMillis = newDateMillis;
    }

//    public long getUpdateInformationLastUpdated() {
//        return updateInformationLastUpdated;
//    }
//    public void setUpdateInformationLastUpdated(final long updateInformationLastUpdated) {
//        this.updateInformationLastUpdated = updateInformationLastUpdated;
//    }

    public int getCountTriedIndices() {
        return countTriedIndices;
    }
    public void incCountTriedIndices() {
        this.countTriedIndices++;
    }
    public int getCurrentIndex() {
        return currentIndex;
    }
    public void setCurrentIndex(final int currentIndex) {
        this.currentIndex = currentIndex;
        if( maxIndex < currentIndex ) {
            maxIndex = currentIndex;
        }
    }
    public int getMaxIndex() {
        return maxIndex;
    }
    public int getMaxSuccessfulIndex() {
        return maxSuccessfulIndex;
    }
    public void updateMaxSuccessfulIndex(final int lMaxSuccessfulIndex) {
        if( this.maxSuccessfulIndex < lMaxSuccessfulIndex ) {
            this.maxSuccessfulIndex = lMaxSuccessfulIndex;
        }
    }
    public int getCountADNF() {
        return countADNF;
    }
    public void incCountADNF() {
        this.countADNF++;
    }
    public int getCountDNF() {
        return countDNF;
    }
    public void incCountDNF() {
        this.countDNF++;
    }
    public int getCountInvalid() {
        return countInvalid;
    }
    public void incCountInvalid() {
        this.countInvalid++;
    }
    public int getCountValid() {
        return countValid;
    }
    public void incCountValid() {
        this.countValid++;
    }
    public String getDateString() {
        return dateString;
    }
    public long getDateMillis() {
        return dateMillis;
    }
    public Board getBoard() {
        return board;
    }
}
