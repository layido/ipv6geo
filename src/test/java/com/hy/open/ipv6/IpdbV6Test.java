package com.hy.open.ipv6;/*
 * Project: ${project_name}
 * 
 * File Created at ${date}
 * 
 * Copyright 2016 CMCC Corporation Limited.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * ZYHY Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license.
 */

import org.junit.Test;

public class IpdbV6Test {

    @Test
    public void test(){
        IpdbV6 ipdbV6 = IpdbV6.getInstance();
        ipdbV6.getIPAddr("2400:da00::dbf:0:100");
    }
}
