import React from "react";

import {Redirect} from "react-router-dom";
import Dashboard from "./containers/Dashboard/Dashboard";
import Transactions from "./containers/Transactions/Transactions";
import Jobs from "./containers/Jobs/Jobs";
import JobDetail from "./containers/Jobs/JobDetail";
import Bridges from "./containers/Bridges/Bridges";
import Login from "./containers/Login";

export const routes = [

  {
    path: "/login",
    label: "Login",
    icon: "fa",
    component: Login
  },
  {
    path: "/jobs/:id",
    label: "JobDetail",
    icon: "fa",
    component: JobDetail
  },
  {
    path: "/jobs",
    label: "Jobs",
    icon: "fa",
    component: Jobs
  },

  {
    path: "/",
    label: "Jobs",
    icon: "fa",
    component: () => <Redirect to="/jobs" />
  },

];

