import React, {Component} from 'react';
import MainWrap from "./MainWrap";
import {store} from "./store";
import {Provider} from "react-redux";

class AppCmp extends Component {

  constructor() {
    super();
    this.state = {
      loading: true,
      store,
    };
  }

  componentDidMount() {

  }

  render() {
    let {store} = this.state;
    return (
        <Provider store={store}>
        <MainWrap/>
        </Provider>
    )
  }
}

export default AppCmp;
