/*
 * Copyright (C) 2007 ETH Zurich
 *
 * This file is part of Fosstrak (www.fosstrak.org).
 *
 * Fosstrak is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * Fosstrak is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Fosstrak; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package org.fosstrak.ale.util;

import java.math.BigInteger;

/**
 * This method provides some methods to convert byte arrays into hexadecimal strings and vice versa.
 * 
 * @author regli
 */
public class HexUtil {

	/**
	 * This method converts a byte array into a hexadecimal string.
	 * 
	 * @param byteArray to convert
	 * @return hexadecimal string (character count twice as many as bytes)
	 */
	public static String byteArrayToHexString(byte[] byteArray) {
		if (byteArray == null) {
			return "";
		}

		StringBuffer buffer = new StringBuffer();

		for (int i = 0; i < byteArray.length; i++) {
			buffer.append(Character.forDigit((byteArray[i] >> 4) & 0xF, 16));
			buffer.append(Character.forDigit(byteArray[i] & 0xF, 16));
		}

		return buffer.toString();
	}

	/**
	 * This method converts a byte array into a binary string.
	 *
	 * @param byteArray to convert
	 * @return hexadecimal string (character count 8x as many as bytes)
	 */
	public static String byteArrayToBinString(byte[] byteArray) {
		if (byteArray == null) {
			return "";
		}

		StringBuffer buffer = new StringBuffer();

		for (int i = 0; i < byteArray.length; i++) {
			for (int b = 7; b >= 0; b--) {
				buffer.append(Character.forDigit( (byteArray[i] >> b) & 1, 2));
			}
		}

		return buffer.toString();
	}
	
	/**
	 * This method converts a hexadecimal string into a byte array.
	 * 
	 * @param hexString to convert
	 * @param minNumBytes Minimum number of bytes. Adds zero bytes to the beginning to achieve this length.
	 * @return byte array
	 */
	public static byte[] hexStringToByteArray(String hexString, int minNumBytes) {
		byte[] data = new BigInteger(hexString, 16).toByteArray();

		// Ensure minimum number of bytes
		if (data.length < minNumBytes) {
			byte[] newData = new byte[minNumBytes];
			System.arraycopy(data, 0, newData, minNumBytes - data.length, data.length);
			return newData;
		}

		return data;
	}
	
}