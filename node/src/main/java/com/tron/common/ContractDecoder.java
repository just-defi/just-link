package com.tron.common;

import static org.tron.tronj.abi.Utils.convert;

import java.util.ArrayList;
import java.util.List;
import org.tron.tronj.abi.FunctionReturnDecoder;
import org.tron.tronj.abi.TypeReference;
import org.tron.tronj.abi.datatypes.Address;
import org.tron.tronj.abi.datatypes.Bool;
import org.tron.tronj.abi.datatypes.Bytes;
import org.tron.tronj.abi.datatypes.Int;
import org.tron.tronj.abi.datatypes.TrcToken;
import org.tron.tronj.abi.datatypes.Type;
import org.tron.tronj.abi.datatypes.Uint;
import org.tron.tronj.abi.datatypes.Utf8String;

public class ContractDecoder {

  public static List<Type> decode(String types, String rawInput) {

    List<TypeReference<?>> typeList = getTypeList(types);
    return FunctionReturnDecoder.decode(rawInput, convert(typeList));
  }

  private static List<TypeReference<?>> getTypeList(String types) {
    List<TypeReference<?>> typeList = new ArrayList<>();

    for(String type : types.split(",")) {
      switch (type) {
        case "bool":
          typeList.add(new TypeReference<Bool>() {});
          break;
        case "uint256":
        case "uint128":
        case "uint64":
        case "uint32":
        case "uint8":
          typeList.add(new TypeReference<Uint>() {});
          break;
        case "int256":
        case "int128":
        case "int64":
        case "int32":
        case "int8":
          typeList.add(new TypeReference<Int>() {});
          break;
        case "address":
          typeList.add(new TypeReference<Address>() {});
          break;
        case "string":
          typeList.add(new TypeReference<Utf8String>() {});
          break;
        case "trcToken":
          typeList.add(new TypeReference<TrcToken>() {});
          break;
        case "bytes":
          typeList.add(new TypeReference<Bytes>() {});
          break;
        default:

      }
    }

    return typeList;
  }
}
